package com.gmm.bot.model;

import com.gmm.bot.enumeration.GemType;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

@Setter
@Getter
public class Player {
    private int id;
    private String displayName;
    private List<Hero> heroes;
    private Set<GemType> heroGemType;

    public Player(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        heroes = new ArrayList<>();
        heroGemType = new LinkedHashSet<>();
    }

    public Optional<Hero> anyHeroFullMana() {
        return heroes.stream().filter(hero -> hero.isAlive() && hero.isFullMana()).findFirst();
    }

    public Hero firstHeroenEmyPlayerAlive() {
//        return heroes.stream().filter(Hero::isAlive).findFirst().orElse(null);
        List<Hero> herolive = heroes.stream().filter(Hero::isAlive).collect(Collectors.toList());
        herolive.forEach(hero -> System.out.println(hero.getName()));
        return herolive.get(0);
    }

    public Hero firstHeroAlive() {
//        return heroes.stream().filter(Hero::isAlive).findFirst().orElse(null);
        List<Hero> herolive = heroes.stream().filter(Hero::isAlive).collect(Collectors.toList());
        herolive.forEach(hero -> System.out.println(hero.getName()));
        return herolive.get(herolive.size()-1);
    }

    public Set<GemType> getRecommendGemType() {
        heroGemType.clear();
        heroes.stream().filter(Hero::isAlive).forEach(hero -> heroGemType.addAll(hero.getGemTypes()));
        heroGemType.add(GemType.SWORD);
        List<GemType> gemTypes = new ArrayList<>(heroGemType);
        Collections.reverse(gemTypes);
        heroGemType.clear();
        heroGemType.addAll(gemTypes);
        return heroGemType;
    }

    public List<Hero> getHeroes() {
        return heroes;
    }
}
