package com.robinhowlett.parsers;

import com.robinhowlett.bcbc.BCBCConfig;
import com.robinhowlett.bcbc.BCBCEntry;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.stream.DoubleStream;

import static org.hamcrest.CoreMatchers.equalTo;

import static java.util.stream.Collectors.summarizingDouble;

public class BCBCParserTest {

    @Test
    public void parse_With2018Results_FinalScoreCalculatedCorrectly() throws Exception {
        File bcbcResults = new File(
                getClass().getClassLoader().getResource("bcbc_2018.pdf").getFile());
        List<BCBCEntry> bcbcEntries = new BCBCParser(new Config2018()).parse(bcbcResults);

        // the Final Score is the starting bankroll of $7,500 plus all winnings minus all bets
        // minus all penalties
        double finalScore = bcbcEntries.stream()
                .filter(bcbcEntry -> bcbcEntry.getUuid().equals("900000129")) // Allen Harberg
                .flatMapToDouble(bcbcEntry -> DoubleStream.of(bcbcEntry.getBets().stream()
                        .collect(summarizingDouble(bet -> (bet.getWinnings() - bet.getBets())))
                        .getSum() - bcbcEntry.getPenalty() + 7500))
                .findAny().getAsDouble();

        Assert.assertThat(finalScore, equalTo(43025d));
    }

    class Config2018 extends BCBCConfig {
        public Config2018() {
            super("20181102", "20181103", "900000721");
        }
    }

    class Config2019 extends BCBCConfig {
        public Config2019() {
            super("20191101", "20191102", "213802");
        }
    }
}
