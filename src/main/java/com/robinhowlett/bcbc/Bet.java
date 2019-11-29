package com.robinhowlett.bcbc;

import org.springframework.util.Assert;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Data;

@Data
public class Bet {

    final String date;
    final int race;
    final String type;
    final Double bets;
    final Double refunds;
    final Double winnings;
    final String runners;

    // Static class Builder
    public static class Builder {
        // matches bets, refunds, winnings, and runners data that the PDFs spits out in one line
        // e.g. $2,000.00 $0.00 $50,420.00 2/5/11
        static final Pattern COMPOSITE_BET_INFO =
                Pattern.compile("\\$([\\d,\\.]+)\\s\\$([\\d,\\.]+)\\s\\$([\\d,\\.]+)\\s(.+)");

        String date;
        int race;
        String compositeBetInfo;
        String type;

        public Bet build() {
            Matcher matcher = COMPOSITE_BET_INFO.matcher(compositeBetInfo);
            Assert.isTrue(matcher.find(), "Valid composite bet info not found");

            return new Bet(
                    date,
                    race,
                    type,
                    Double.valueOf(matcher.group(1).replaceAll(",", "")),
                    Double.valueOf(matcher.group(2).replaceAll(",", "")),
                    Double.valueOf(matcher.group(3).replaceAll(",", "")),
                    matcher.group(4)
            );
        }

        public Builder date(String date) {
            this.date = date;
            return this;
        }

        public Builder race(int raceNumber) {
            this.race = raceNumber;
            return this;
        }

        public Builder type(String betType) {
            this.type = sanitizeBetType(betType);
            return this;
        }

        public Builder compositeBetInfo(String compositeBetInfo) {
            // this part also handles when the bets details were so long they bled over to the next
            // line. When this happens, save the combined text
            this.compositeBetInfo = (this.compositeBetInfo != null ?
                    this.compositeBetInfo.concat(compositeBetInfo.trim()) :
                    compositeBetInfo)
                    // day 2 seemed to use the pipe character | instead of the traditional
                    // comma character , to separate different selections within the same race.
                    // So I'm standardizing on comma
                    .replaceAll("\\|", ",");
            return this;
        }

        // learned from analysis via Excel
        // the BCBC used inconsistent labels for the same bet types (hand entered?)
        // this standardizes them
        private String sanitizeBetType(String betType) {
            // 3 different values used!
            if (betType.trim().equals("DB") || betType.trim().equals("DBL") ||
                    betType.trim().equals("DOUBLE")) {
                betType = "DD";
            } else if (betType.trim().equals("EXA") ||
                    betType.trim().equals("EXACTA")) {
                betType = "EX";
            } else if (betType.trim().equals("PLACE") || betType.trim().equals("PL")) {
                betType = "PLC";
            } else if (betType.trim().equals("TRIFECTA") ||
                    betType.trim().equals("TR") ||
                    // really odd! a superfecta? not allowed by the tournament rules, but looks
                    // like TRI
                    betType.trim().equals("SF")) {
                betType = "TRI";
            } else if (betType.trim().equals("SH") || betType.trim().equals("SHOW")) {
                betType = "SHW";
            } else if (betType.trim().equals("WIN") || betType.trim().equals("WN")) {
                betType = "WIN";
            }
            return betType;
        }
    }
}
