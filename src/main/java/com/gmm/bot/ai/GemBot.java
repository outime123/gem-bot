package com.gmm.bot.ai;

import com.gmm.bot.enumeration.GemType;
import com.gmm.bot.model.Grid;
import com.gmm.bot.model.Hero;
import com.gmm.bot.model.Pair;
import com.gmm.bot.model.Player;
import com.smartfoxserver.v2.entities.data.ISFSArray;
import com.smartfoxserver.v2.entities.data.ISFSObject;
import com.smartfoxserver.v2.entities.data.SFSObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import sfs2x.client.entities.Room;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Scope("prototype")
@Slf4j
@Getter
public class GemBot extends BaseBot{

    @Override
    protected void swapGem(SFSObject params) {
        boolean isValidSwap = params.getBool("validSwap");
        if (!isValidSwap) {
            return;
        }
        handleGems(params);
    }

    @Override
    protected void handleGems(ISFSObject params) {
        ISFSObject gameSession = params.getSFSObject("gameSession");
        currentPlayerId = gameSession.getInt("currentPlayerId");
        //get last snapshot
        ISFSArray snapshotSfsArray = params.getSFSArray("snapshots");
        ISFSObject lastSnapshot = snapshotSfsArray.getSFSObject(snapshotSfsArray.size() - 1);
        boolean needRenewBoard = params.containsKey("renewBoard");
        // update information of hero
        handleHeroes(lastSnapshot);
        if (needRenewBoard) {
            grid.updateGems(params.getSFSArray("renewBoard"),null);
            taskScheduler.schedule(new FinishTurn(false), new Date(System.currentTimeMillis() + delaySwapGem));
            return;
        }
        // update gem
        grid.setGemTypes(botPlayer.getRecommendGemType());
        ISFSArray gemCodes = lastSnapshot.getSFSArray("gems");
        ISFSArray gemModifiers = lastSnapshot.getSFSArray("gemModifiers");
        grid.updateGems(gemCodes,gemModifiers);
        taskScheduler.schedule(new FinishTurn(false), new Date(System.currentTimeMillis() + delaySwapGem));
    }

    @Override
    protected void startTurn(ISFSObject params) {
        currentPlayerId = params.getInt("currentPlayerId");
        if (!isBotTurn()) {
            return;
        }
        Optional<Hero> heroFullMana = botPlayer.anyHeroFullMana();
        if (heroFullMana.isPresent()) {
            taskScheduler.schedule(new SendReQuestSkill(heroFullMana.get()), new Date(System.currentTimeMillis() + delaySwapGem));
            return;
        }
        taskScheduler.schedule(new SendRequestSwapGem(), new Date(System.currentTimeMillis() + delaySwapGem));
    }

    private void handleHeroes(ISFSObject params) {
        ISFSArray heroesBotPlayer = params.getSFSArray(botPlayer.getDisplayName());
        for (int i = 0; i < botPlayer.getHeroes().size(); i++) {
            botPlayer.getHeroes().get(i).updateHero(heroesBotPlayer.getSFSObject(i));
        }

        ISFSArray heroesEnemyPlayer = params.getSFSArray(enemyPlayer.getDisplayName());
        for (int i = 0; i < enemyPlayer.getHeroes().size(); i++) {
            enemyPlayer.getHeroes().get(i).updateHero(heroesEnemyPlayer.getSFSObject(i));
        }
    }

    @Override
    protected void startGame(ISFSObject gameSession, Room room) {
        // Assign Bot player & enemy player
        assignPlayers(room);

        // Player & Heroes
        ISFSObject objBotPlayer = gameSession.getSFSObject(botPlayer.getDisplayName());
        ISFSObject objEnemyPlayer = gameSession.getSFSObject(enemyPlayer.getDisplayName());

        ISFSArray botPlayerHero = objBotPlayer.getSFSArray("heroes");
        ISFSArray enemyPlayerHero = objEnemyPlayer.getSFSArray("heroes");

        for (int i = 0; i < botPlayerHero.size(); i++) {
            botPlayer.getHeroes().add(new Hero(botPlayerHero.getSFSObject(i)));
        }
        for (int i = 0; i < enemyPlayerHero.size(); i++) {
            enemyPlayer.getHeroes().add(new Hero(enemyPlayerHero.getSFSObject(i)));
        }

        // Gems
        grid = new Grid(gameSession.getSFSArray("gems"),null, botPlayer.getRecommendGemType());
        currentPlayerId = gameSession.getInt("currentPlayerId");
        log("Initial game ");
        taskScheduler.schedule(new FinishTurn(true), new Date(System.currentTimeMillis() + delaySwapGem));
    }

    protected GemType selectGem() {
        return botPlayer.getRecommendGemType().stream().filter(gemType -> grid.getGemTypes().contains(gemType)).findFirst().orElseGet(null);
    }

    protected boolean isBotTurn() {
        return botPlayer.getId() == currentPlayerId;
    }

    private class FinishTurn implements Runnable {
        private final boolean isFirstTurn;

        public FinishTurn(boolean isFirstTurn) {
            this.isFirstTurn = isFirstTurn;
        }

        @Override
        public void run() {
            SFSObject data = new SFSObject();
            data.putBool("isFirstTurn", isFirstTurn);
            log("sendExtensionRequest()|room:" + room.getName() + "|extCmd:" + ConstantCommand.FINISH_TURN + " first turn " + isFirstTurn);
            sendExtensionRequest(ConstantCommand.FINISH_TURN, data);
        }
    }

    private class SendReQuestSkill implements Runnable {
        private final Hero heroCastSkill;

        public SendReQuestSkill(Hero heroCastSkill) {
            this.heroCastSkill = heroCastSkill;
        }

        @Override
        public void run() {
            data.putUtfString("casterId", heroCastSkill.getId().toString());
            if (heroCastSkill.isHeroSelfSkill()) {
                data.putUtfString("targetId", botPlayer.firstHeroAlive().getId().toString());
            } else {
                data.putUtfString("targetId", enemyPlayer.firstHeroAlive().getId().toString());
            }
            data.putUtfString("selectedGem", String.valueOf(selectGem().getCode()));
            data.putUtfString("gemIndex", String.valueOf(ThreadLocalRandom.current().nextInt(64)));
            data.putBool("isTargetAllyOrNot",false);
            log("sendExtensionRequest()|room:" + room.getName() + "|extCmd:" + ConstantCommand.USE_SKILL + "|Hero cast skill: " + heroCastSkill.getName());
            sendExtensionRequest(ConstantCommand.USE_SKILL, data);
        }

    }

    private class SendRequestSwapGem implements Runnable {
        @Override
        public void run() {
            Pair<Integer> indexSwap = grid.recommendSwapGem();
            data.putInt("index1", indexSwap.getParam1());
            data.putInt("index2", indexSwap.getParam2());
            log("sendExtensionRequest()|room:" + room.getName() + "|extCmd:" + ConstantCommand.SWAP_GEM + "|index1: " + indexSwap.getParam1() + " index1: " + indexSwap.getParam2());
            sendExtensionRequest(ConstantCommand.SWAP_GEM, data);
        }
    }
}
