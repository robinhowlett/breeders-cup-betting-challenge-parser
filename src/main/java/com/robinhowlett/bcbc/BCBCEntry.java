package com.robinhowlett.bcbc;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Data;

@Data
public class BCBCEntry {
    static final Pattern PENALTY_AMOUNT = Pattern.compile("^\\$([\\d,\\.]+)");

    final String first;
    final String last;
    final String name;
    final String uuid;
    final String adw;
    final List<String> playerText;
    List<Bet> bets;
    double penalty;

    public BCBCEntry(List<String> playerText) {
        this.bets = new ArrayList<>();

        this.last = playerText.remove(0);
        this.first = playerText.remove(0);
        this.uuid = playerText.remove(0);
        this.adw = playerText.remove(0);
        this.name = playerText.remove(0);

        setPenalty(playerText.remove(playerText.size() - 1));

        this.playerText = playerText;
    }

    public void setPenalty(String word) {
        Matcher matcher = PENALTY_AMOUNT.matcher(word);
        if (matcher.find()) {
            penalty = Double.valueOf(matcher.group(1).replaceAll(",", ""));
        }
    }
}
