package com.robinhowlett.parsers;

import com.robinhowlett.bcbc.BCBCConfig;
import com.robinhowlett.bcbc.BCBCEntry;
import com.robinhowlett.bcbc.Bet;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

public class BCBCParser extends PDFTextStripper implements Parser<List<BCBCEntry>, File> {
    static final Logger LOGGER = LoggerFactory.getLogger(BCBCParser.class);

    // regex to match bets e.g. EX, TRI, DD, WIN
    private static final Pattern BET_TYPE = Pattern.compile("^([A-Z-])+$");
    private final BCBCConfig config;
    private List<String> bcbcEntryRelatedText;
    private List<BCBCEntry> bcbcEntries;

    /**
     * Instantiate a new PDFTextStripper object.
     *
     * @throws IOException If there is an error loading the properties.
     */
    public BCBCParser(BCBCConfig config) throws IOException {
        super();
        this.config = config;
        bcbcEntryRelatedText = new ArrayList<>();
        bcbcEntries = new ArrayList<>();

        // see https://pdfbox.apache.org/2.0/getting-started.html
        System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
    }

    @Override
    public List<BCBCEntry> parse(File bcbcResults) {
        try (PDDocument bcbcResultsPdf = PDDocument.load(bcbcResults)) {
            setSortByPosition(true);

            try (Writer devNullWriter = new OutputStreamWriter(new OutputStream() {
                @Override
                public void write(int b) {
                    // discard everything
                }
            }, UTF_8)) {
                // this will end up calling #writeString() below with the text of each line
                // each line of text can then be examined in the context of the text already
                // parsed, so can figure out if the text is related to the player or the bet etc
                // the line of text itself will be pulled apart to extract the actual bets
                writeText(bcbcResultsPdf, devNullWriter);
                // at this point, all the players' start-end line indexes have been saved
            }
        } catch (InvalidPasswordException e) {
            LOGGER.error("PDF Password incorrect", e);
        } catch (IOException e) {
            LOGGER.error("Error parsing PDF", e);
        }

        bcbcEntries.forEach(bcbcEntry ->
                bcbcEntry.getBets().addAll(createBetsForEntry(config, bcbcEntry)));

        return bcbcEntries;
    }

    // triggered during stripper.writeText() execution above (by the private stripper.writeLine()
    // method) and called for each "word" of a line processed when parsing the PDF (sometimes a word
    // may actually be a single space-separated piece of text)
    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        // collect all the words relevant for this entry
        bcbcEntryRelatedText.add(string);

        // if "Final Score" was found, then we at the end of content about this player
        if (string.contains("Final Score")) {
            BCBCEntry entry = new BCBCEntry(bcbcEntryRelatedText);
            bcbcEntries.add(entry);

            // reset
            bcbcEntryRelatedText = new ArrayList<>();
        }
    }

    // word-by-word parsing and building the data structure
    private List<Bet> createBetsForEntry(BCBCConfig config, BCBCEntry entry) {
        List<Bet> bets = new ArrayList<>();
        boolean day2Found = false;
        boolean betTypeFound = false;
        boolean activeBetComplete = false;
        boolean penaltyAmountFound = false;
        int race = 0;

        Bet.Builder betBuilder = new Bet.Builder();

        for (String word : entry.getPlayerText()) {
            // if it's Saturday's date, then Friday bets must be done
            if (word.equals("Date: " + config.getDay2Date())) {
                day2Found = true;
                continue; // go to the next word (it should be the race number)
            }

            // track the race these bets were for
            if (word.startsWith("Race: ")) {
                race = Integer.parseInt(word.substring(word.lastIndexOf(' ') + 1));
                continue; // go to the next word (it should be the start of the betBuilder table)
            }

            // update the entry with the total amount of penalties incurred
            if (penaltyAmountFound) {
                entry.setPenalty(word);
                penaltyAmountFound = false; // reset
            }

            // only betBuilder types (Exacta, Trifecta etc.) are in all-caps around the
            // betBuilder data
            // set this market because next word/line (or two, if wrapped) will be related to
            // a betBuilder
            boolean betTypeDetected = BET_TYPE.matcher(word).find();
            // race totals are derived summary data that aggregate the lines above it
            // we don't need to parse it but it is a marker that all bets for this race have
            // been parsed
            boolean raceTotalsSummaryLineDetected = (word.split(" ").length == 3);

            // first check for any bets that have not yet been fully parsed
            if (betTypeFound) {
                boolean completionOfActiveBetDetected =
                        (betTypeDetected || raceTotalsSummaryLineDetected);
                // check if a betBuilder description has wrapped to the next line and update it
                // if so
                if (!completionOfActiveBetDetected) {
                    // because of trailing spaces between the "Bets", "Refunds", "Winnings", and
                    // "Runners" column values, this "word" contains values intended for
                    // multiple columns
                    //
                    // the setter handles splitting this into its respective components as well as
                    // handling line-wraps and inconsistent delimiters
                    betBuilder.compositeBetInfo(word);
                }
                activeBetComplete = true;
            }

            // build and save the bet if it is ready
            if (activeBetComplete) {
                // set the date and race number as they can apply to multiple bets
                betBuilder.date(day2Found ? config.getDay2Date() : config.getDay1Date());
                betBuilder.race(race);

                bets.add(betBuilder.build());

                // start clean for the next betBuilder
                betBuilder = new Bet.Builder();
                betTypeFound = false;
                activeBetComplete = false;
            }

            // set the marker that a betBuilder is active (ready to be parsed)
            if (betTypeDetected) {
                betTypeFound = true;
                betBuilder.type(word);
                // go to the next word (it should be the composite betBuilder information)
            }

            // set a flag that the next word parsed contains the penalty amount for the entry
            if (word.contains("Penalty Amount:")) {
                penaltyAmountFound = true;
            }
        }

        return bets;
    }
}
