import javax.swing.*;
import javax.swing.text.Style;
import javax.swing.border.EmptyBorder;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Part 2 GUI module for the Typing Race Simulator.
 *
 * This class uses only Java Swing, so it does not need external libraries.
 * It provides:
 * - Passage selection / custom passage
 * - Seat count selection from 2 to 6
 * - Difficulty modifiers: autocorrect, caffeine mode, night shift
 * - Custom typists: name, symbol, typing style, keyboard type, colour, accessory
 * - Animated race progress bars
 * - Race statistics: WPM, accuracy percentage, burnout count, accuracy change
 * - Personal best WPM and race history
 * - Leaderboard / ranking system
 * - Comparison view for two typists
 *
 * RaceTypist extends Typist (from Part 1) so that all core race logic
 * — progress tracking, burnout, slide-back, accuracy clamping — is
 * inherited from the shared Typist class rather than duplicated here.
 *
 * Place this file in the Part2 folder alongside Typist.java.
 * Compile with:  javac Typist.java TypingRaceGUI.java
 * Run with:      java TypingRaceGUI
 */
public class TypingRaceGUI
{
    private JFrame frame;
    private JPanel setupPanel;
    private JPanel racePanel;
    private JPanel typistConfigPanel;
    private JPanel lanesPanel;
    private JPanel statsPanel;
    private JTextArea historyArea;
    private JTextArea leaderboardArea;
    private JTextArea comparisonArea;
    private JTextArea typistHistoryArea;
    private JSplitPane split;


    private JComboBox<String> passageChoice;
    private JTextArea customPassageArea;
    private JSpinner seatCountSpinner;
    private JCheckBox autocorrectBox;
    private JCheckBox caffeineBox;
    private JCheckBox nightShiftBox;
    private JButton startButton;
    private JButton resetButton;
    private JButton compareButton;
    private JComboBox<String> compareTypistOne;
    private JComboBox<String> compareTypistTwo;

    private Timer raceTimer;
    private final List<TypistSetupRow> setupRows;
    private final List<RaceTypist> racers;
    private final Map<String, Double> personalBestWpm;
    private final Map<String, Integer> leaderboardPoints;
    private int raceNumber;
    private int turnNumber;
    private long raceStartTime;
    private String currentPassage;
    private int passageLength;
    private boolean raceRunning;

    private static final int TIMER_DELAY_MS = 180;
    private static final int DEFAULT_TRACK_LENGTH = 60;
    private static final double BASE_BURNOUT_CHANCE = 0.035;
    private static final int NORMAL_SLIDE_BACK = 2;
    private static final int NORMAL_BURNOUT_DURATION = 3;

    public TypingRaceGUI()
    {
        setupRows = new ArrayList<TypistSetupRow>();
        racers = new ArrayList<RaceTypist>();
        personalBestWpm = new HashMap<String, Double>();
        leaderboardPoints = new HashMap<String, Integer>();
        raceNumber = 0;
        raceRunning = false;
    }

    /**
     * Required entry point for the GUI version.
     */
    public void startRaceGUI()
    {
        SwingUtilities.invokeLater(new Runnable()
        {
            @Override public void run()
            {
                buildFrame();
            }
        });
    }

    /**
     * Allows the GUI to be run directly from the command line.
     */
    public static void main(String[] args)
    {
        TypingRaceGUI gui = new TypingRaceGUI();
        gui.startRaceGUI();
    }

    private void buildFrame()
    {
        frame = new JFrame("Typing Race Simulator - GUI");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1100, 760);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout(10, 10));

        setupPanel = createSetupPanel();
        racePanel = createRacePanel();

        frame.add(setupPanel, BorderLayout.WEST);
        frame.add(racePanel, BorderLayout.CENTER);

        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> split.setDividerLocation(0.5));
    }

    private JPanel createSetupPanel()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        panel.setPreferredSize(new Dimension(320, 720));

        JPanel topSettings = new JPanel();
        topSettings.setLayout(new BoxLayout(topSettings, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Race Configuration");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        topSettings.add(title);
        topSettings.add(Box.createVerticalStrut(10));

        // --- Passage selection ---
        JLabel passageLabel = new JLabel("Passage:");
        passageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topSettings.add(passageLabel);
        topSettings.add(Box.createVerticalStrut(3));

        passageChoice = new JComboBox<String>(new String[] {
            "Short passage",
            "Medium passage",
            "Long passage",
            "Custom passage"
        });
        passageChoice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        passageChoice.setAlignmentX(Component.LEFT_ALIGNMENT);
        topSettings.add(passageChoice);
        topSettings.add(Box.createVerticalStrut(8));

        // --- Custom passage text area (taller than other rows) ---
        JLabel customLabel = new JLabel("Custom passage text:");
        customLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topSettings.add(customLabel);
        topSettings.add(Box.createVerticalStrut(3));

        customPassageArea = new JTextArea(5, 25);
        customPassageArea.setLineWrap(true);
        customPassageArea.setWrapStyleWord(true);
        customPassageArea.setText("The quick brown fox jumps over the lazy dog.");

        JScrollPane customScroll = new JScrollPane(customPassageArea);
        customScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        customScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        customScroll.setPreferredSize(new Dimension(0, 90));
        topSettings.add(customScroll);
        topSettings.add(Box.createVerticalStrut(8));

        // --- Seat count spinner ---
        JLabel seatLabel = new JLabel("Number of typists:");
        seatLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topSettings.add(seatLabel);
        topSettings.add(Box.createVerticalStrut(3));

        seatCountSpinner = new JSpinner(new SpinnerNumberModel(3, 2, 6, 1));
        seatCountSpinner.addChangeListener(e -> refreshTypistRows());
        seatCountSpinner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        seatCountSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);
        topSettings.add(seatCountSpinner);
        topSettings.add(Box.createVerticalStrut(10));

        // --- Difficulty modifiers with a visible separator ---
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 2));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        topSettings.add(sep);
        topSettings.add(Box.createVerticalStrut(6));

        JLabel modLabel = new JLabel("Difficulty Modifiers:");
        modLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        modLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topSettings.add(modLabel);
        topSettings.add(Box.createVerticalStrut(4));

        autocorrectBox = new JCheckBox("Autocorrect: slide-back is reduced");
        caffeineBox    = new JCheckBox("Caffeine Mode: early boost, later burnout risk");
        nightShiftBox  = new JCheckBox("Night Shift: everyone loses some accuracy");

        for (JCheckBox box : new JCheckBox[]{autocorrectBox, caffeineBox, nightShiftBox})
        {
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            topSettings.add(box);
            topSettings.add(Box.createVerticalStrut(3));
        }

        topSettings.add(Box.createVerticalStrut(6));

        panel.add(topSettings, BorderLayout.NORTH);

        // --- Typist config scroll area fills remaining space ---
        typistConfigPanel = new JPanel();
        typistConfigPanel.setLayout(new BoxLayout(typistConfigPanel, BoxLayout.Y_AXIS));
        JScrollPane typistScroll = new JScrollPane(typistConfigPanel);
        typistScroll.setBorder(BorderFactory.createTitledBorder("Customise Typists"));
        panel.add(typistScroll, BorderLayout.CENTER);

        // --- Buttons with a fixed preferred height ---
        JPanel buttons = new JPanel();
        buttons.setLayout(new GridLayout(1, 2, 8, 0));
        buttons.setBorder(new EmptyBorder(8, 0, 0, 0));
        startButton = new JButton("Start Race");
        resetButton = new JButton("Reset Race");
        startButton.setPreferredSize(new Dimension(0, 36));
        resetButton.setPreferredSize(new Dimension(0, 36));
        startButton.addActionListener(e -> startRaceFromSetup());
        resetButton.addActionListener(e -> resetRaceDisplay());
        buttons.add(startButton);
        buttons.add(resetButton);
        panel.add(buttons, BorderLayout.SOUTH);

        refreshTypistRows();
        return panel;
    }

    private JPanel createRacePanel()
    {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("Typing Race Track");
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        panel.add(title, BorderLayout.NORTH);

        // Build lanesPanel first
        lanesPanel = new JPanel();
        lanesPanel.setLayout(new BoxLayout(lanesPanel, BoxLayout.Y_AXIS));
        lanesPanel.setBorder(BorderFactory.createTitledBorder("Race Lanes"));

        // Build statsPanel and populate it
        statsPanel = new JPanel(new GridLayout(2, 2, 8, 8));

        historyArea      = createOutputArea("Race history will appear here.");
        leaderboardArea  = createOutputArea("Leaderboard will appear here.");
        comparisonArea   = createOutputArea("Select two typists after a race to compare them.");
        typistHistoryArea = createOutputArea("Per-typist history will appear here after a race.");

        statsPanel.add(wrapArea("Race History", historyArea));
        statsPanel.add(wrapArea("Leaderboard", leaderboardArea));
        statsPanel.add(wrapComparisonArea());
        statsPanel.add(wrapArea("Typist History", typistHistoryArea));

        // Now both panels exist — safe to create the split
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(lanesPanel), statsPanel);
        split.setResizeWeight(0.5);
        split.setDividerSize(6);
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    private JTextArea createOutputArea(String text)
    {
        JTextArea area = new JTextArea(10, 20);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setText(text);
        return area;
    }

    private JPanel wrapArea(String title, JTextArea area)
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        return panel;
    }

    private JPanel wrapComparisonArea()
    {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Comparison View"));

        JPanel choices = new JPanel(new GridLayout(3, 1, 5, 5));
        compareTypistOne = new JComboBox<String>();
        compareTypistTwo = new JComboBox<String>();
        compareButton = new JButton("Compare");
        compareButton.addActionListener(e -> compareSelectedTypists());

        choices.add(compareTypistOne);
        choices.add(compareTypistTwo);
        choices.add(compareButton);

        panel.add(choices, BorderLayout.NORTH);
        panel.add(new JScrollPane(comparisonArea), BorderLayout.CENTER);
        return panel;
    }

    private void refreshTypistRows()
    {
        if (typistConfigPanel == null)
        {
            return;
        }

        typistConfigPanel.removeAll();
        setupRows.clear();

        int count = (Integer) seatCountSpinner.getValue();
        char[] defaultSymbols = {'①', '②', '③', '④', '⑤', '⑥'};
        String[] defaultNames = {
            "TURBOFINGERS",
            "QWERTY_QUEEN",
            "HUNT_N_PECK",
            "KEYBOARD_KING",
            "TYPE_MASTER",
            "SPACEBAR_STAR"
        };

        for (int i = 0; i < count; i++)
        {
            TypistSetupRow row = new TypistSetupRow(i + 1, defaultNames[i], String.valueOf(defaultSymbols[i]));
            setupRows.add(row);
            typistConfigPanel.add(row.panel);
        }

        typistConfigPanel.revalidate();
        typistConfigPanel.repaint();
    }

    private void startRaceFromSetup()
    {
        if (raceRunning)
        {
            JOptionPane.showMessageDialog(frame, "A race is already running.");
            return;
        }

        currentPassage = getSelectedPassage();
        passageLength = currentPassage.length();
        if (passageLength < 1)
        {
            passageLength = DEFAULT_TRACK_LENGTH;
            currentPassage = "Default typing race passage used because the custom passage was empty.";
        }

        racers.clear();
        lanesPanel.removeAll();

        for (TypistSetupRow row : setupRows)
        {
            String name = row.nameField.getText().trim();
            Integer pts = leaderboardPoints.get(name);
            if (pts != null && pts >= 10)
            {
                row.currentRankModifier = -0.02; // top ranked face harder expectations
            }
            else if (pts != null && pts >= 5)
            {
                row.currentRankModifier = -0.01;
            }
            else
            {
                row.currentRankModifier = 0.0;
            }
        }
        turnNumber = 0;
        raceNumber++;

        for (int i = 0; i < setupRows.size(); i++)
        {
            TypistSetupRow row = setupRows.get(i);
            RaceTypist racer = buildRaceTypistFromRow(row, i);
            racers.add(racer);
            lanesPanel.add(racer.lanePanel);
        }

        updateComparisonChoices();
        lanesPanel.revalidate();
        lanesPanel.repaint();

        raceStartTime = System.currentTimeMillis();
        raceRunning = true;
        startButton.setEnabled(false);

        raceTimer = new Timer(TIMER_DELAY_MS, e -> raceTurn());
        raceTimer.start();
    }

    private String getSelectedPassage()
    {
        String selected = (String) passageChoice.getSelectedItem();

        if ("Short passage".equals(selected))
        {
            return "The quick brown fox jumps over the lazy dog.";
        }
        else if ("Medium passage".equals(selected))
        {
            return "Object oriented programming helps developers model programs using classes, objects, fields and methods.";
        }
        else if ("Long passage".equals(selected))
        {
            return "In a typing race, competitors must balance speed, accuracy and stamina. A fast typist may gain an early lead, but repeated mistakes and burnout can completely change the final result.";
        }
        else
        {
            return customPassageArea.getText().trim();
        }
    }

    private RaceTypist buildRaceTypistFromRow(TypistSetupRow row, int index)
    {
        String name = row.nameField.getText().trim();
        if (name.length() == 0)
        {
            name = "Typist " + (index + 1);
        }

        String symbolText = row.symbolField.getText().trim();
        // Symbol is now a char to match Typist's constructor signature
        char symbol = '?';
        if (symbolText.length() > 0)
        {
            symbol = symbolText.charAt(0);
        }

        double accuracy = 0.65;
        double speedMultiplier = 1.0;
        double burnoutMultiplier = 1.0;
        int burnoutDuration = NORMAL_BURNOUT_DURATION;
        int slideBackAmount = NORMAL_SLIDE_BACK;

        String style = (String) row.styleBox.getSelectedItem();
        if ("Touch Typist".equals(style))
        {
            accuracy += 0.20;
            speedMultiplier += 0.15;
            burnoutMultiplier += 0.20;
        }
        else if ("Hunt & Peck".equals(style))
        {
            accuracy -= 0.15;
            speedMultiplier -= 0.20;
            burnoutMultiplier -= 0.20;
        }
        else if ("Phone Thumbs".equals(style))
        {
            accuracy -= 0.05;
            speedMultiplier += 0.05;
        }
        else if ("Voice-to-Text".equals(style))
        {
            accuracy += 0.10;
            speedMultiplier -= 0.05;
        }

        String keyboard = (String) row.keyboardBox.getSelectedItem();
        if ("Mechanical".equals(keyboard))
        {
            accuracy += 0.05;
            speedMultiplier += 0.10;
        }
        else if ("Touchscreen".equals(keyboard))
        {
            accuracy -= 0.08;
            slideBackAmount += 1;
        }
        else if ("Stenography".equals(keyboard))
        {
            accuracy += 0.15;
            speedMultiplier += 0.25;
            burnoutMultiplier += 0.25;
        }

        String accessory = (String) row.accessoryBox.getSelectedItem();
        if ("Wrist Support".equals(accessory))
        {
            burnoutDuration = 2;
            burnoutMultiplier -= 0.20;
        }
        else if ("Noise-Cancelling Headphones".equals(accessory))
        {
            accuracy += 0.08;
        }

        if (autocorrectBox.isSelected())
        {
            slideBackAmount = Math.max(1, slideBackAmount / 2);
        }
        if (nightShiftBox.isSelected())
        {
            accuracy -= 0.08;
        }

        accuracy += row.currentRankModifier;
        accuracy = clamp(accuracy, 0.05, 0.98);
        speedMultiplier = clamp(speedMultiplier, 0.45, 1.60);
        burnoutMultiplier = clamp(burnoutMultiplier, 0.20, 2.00);

        Color colour = getColourFromName((String) row.colourBox.getSelectedItem());

        RaceTypist racer = new RaceTypist(
            symbol,
            name,
            accuracy,
            speedMultiplier,
            burnoutMultiplier,
            slideBackAmount,
            burnoutDuration,
            colour,
            style,
            keyboard,
            accessory
        );

        return racer;
    }

    private void raceTurn()
    {
        turnNumber++;
        boolean anyFinished = false;

        for (RaceTypist racer : racers)
        {
            if (!racer.finished)
            {
                advanceRacer(racer);
            }

            // Use Typist's getProgress() to check the finish condition
            if (racer.getProgress() >= passageLength)
            {
                racer.finished = true;
                anyFinished = true;
            }

            updateLaneDisplay(racer);
        }

        if (anyFinished)
        {
            finishRace();
        }
    }

    private void advanceRacer(RaceTypist racer)
    {
        // Use Typist's isBurntOut() and recoverFromBurnout()
        if (racer.isBurntOut())
        {
            racer.recoverFromBurnout();
            return;
        }

        // Use Typist's getAccuracy() instead of direct field access
        double currentAccuracy = racer.getAccuracy();
        double currentSpeed = racer.speedMultiplier;
        double currentBurnoutMultiplier = racer.burnoutMultiplier;

        if ("Energy Drink".equals(racer.accessory))
        {
            double progressFraction = (double) racer.getProgress() / passageLength;
            if (progressFraction < 0.5)
            {
                currentAccuracy = clamp(currentAccuracy + 0.10, 0.0, 1.0);
            }
            else
            {
                currentAccuracy = clamp(currentAccuracy - 0.10, 0.0, 1.0);
            }
        }

        if (caffeineBox.isSelected())
        {
            if (turnNumber <= 10)
            {
                currentAccuracy = clamp(currentAccuracy + 0.08, 0.0, 1.0);
                currentSpeed += 0.20;
            }
            else
            {
                currentBurnoutMultiplier += 0.35;
            }
        }

        racer.totalKeystrokes++;

        if (Math.random() < currentAccuracy)
        {
            // Use Typist's typeCharacter() for forward movement
            racer.typeCharacter();

            // Speed multiplier above 1.0 gives a chance of advancing a second character
            if (Math.random() < (currentSpeed - 1.0))
            {
                racer.typeCharacter();
            }
            racer.correctKeystrokes++;
        }
        else
        {
            racer.mistypes++;
            racer.justMistyped = true;
            // Use Typist's slideBack() — it handles the clamp to zero automatically
            racer.slideBack(racer.slideBackAmount);
        }

        double burnoutChance = BASE_BURNOUT_CHANCE * currentAccuracy * currentAccuracy * currentBurnoutMultiplier;
        if (Math.random() < burnoutChance)
        {
            racer.burnoutCount++;
            // Use Typist's burnOut() instead of setting the field directly
            racer.burnOut(racer.burnoutDuration);
        }
    }

    private void updateLaneDisplay(RaceTypist racer)
    {
        // Use Typist's getProgress() for display
        int safeProgress = racer.getProgress();
        if (safeProgress > passageLength)
        {
            safeProgress = passageLength;
        }

        racer.progressBar.setMaximum(passageLength);
        racer.progressBar.setValue(safeProgress);
        racer.progressBar.setString(safeProgress + " / " + passageLength + " chars");

        try
        {
            StyledDocument doc = racer.passagePane.getStyledDocument();
            Style defaultStyle = StyleContext.getDefaultStyleContext()
                .getStyle(StyleContext.DEFAULT_STYLE);
            Style completedStyle = racer.passagePane.addStyle("completed", null);
            StyleConstants.setBackground(completedStyle, new Color(180, 230, 180));

            doc.setCharacterAttributes(0, currentPassage.length(), defaultStyle, true);
            if (safeProgress > 0)
            {
                doc.setCharacterAttributes(0, safeProgress, completedStyle, false);
            }
        }
        catch (Exception ex)
        {
            // silently ignore styling errors
        }

        // Use Typist getters: getSymbol(), getName(), getAccuracy(),
        // isBurntOut(), getBurnoutTurnsRemaining()
        String status = racer.getSymbol() + " " + racer.getName();
        status += " | Accuracy rating: " + format(racer.getAccuracy());

        if (racer.isBurntOut())
        {
            status += " | BURNT OUT (" + racer.getBurnoutTurnsRemaining() + " turns)";
        }
        else if (racer.justMistyped)
        {
            status += " | just mistyped [<]";
        }
        else
        {
            status += " | typing";
        }

        racer.statusLabel.setText(status);
        racer.justMistyped = false;
    }

    private void finishRace()
    {
        raceRunning = false;
        startButton.setEnabled(true);

        if (raceTimer != null)
        {
            raceTimer.stop();
        }

        long raceEndTime = System.currentTimeMillis();
        double elapsedMinutes = Math.max((raceEndTime - raceStartTime) / 60000.0, 0.01);

        List<RaceTypist> sorted = new ArrayList<RaceTypist>(racers);
        Collections.sort(sorted, new Comparator<RaceTypist>()
        {
            @Override public int compare(RaceTypist a, RaceTypist b)
            {
                // Use Typist's getProgress() for sorting
                if (b.getProgress() != a.getProgress())
                {
                    return b.getProgress() - a.getProgress();
                }
                return a.burnoutCount - b.burnoutCount;
            }
        });

        StringBuilder raceSummary = new StringBuilder();
        raceSummary.append("Race ").append(raceNumber).append(" finished\n");
        raceSummary.append("Passage length: ").append(passageLength).append(" characters\n");
        // Use Typist's getName() throughout
        raceSummary.append("Winner: ").append(sorted.get(0).getName()).append("\n\n");

        for (int i = 0; i < sorted.size(); i++)
        {
            RaceTypist racer = sorted.get(i);
            int position = i + 1;

            racer.wpm = calculateWpm(racer.getProgress(), elapsedMinutes);
            racer.accuracyPercentage = calculateAccuracyPercentage(racer);
            updateAccuracyAfterRace(racer, position);
            updatePersonalBest(racer);

            if (position == 1)
            {
                racer.consecutiveWins++;
            }
            else
            {
                racer.consecutiveWins = 0;
            }

            if (racer.burnoutCount == 0)
            {
                racer.racesWithoutBurnout++;
            }
            else
            {
                racer.racesWithoutBurnout = 0;
            }

            updateLeaderboard(racer, position);
            racer.raceHistory.add("Race " + raceNumber + ": position " + position
                + ", WPM " + format(racer.wpm)
                + ", accuracy " + format(racer.accuracyPercentage) + "%"
                + ", burnouts " + racer.burnoutCount);

            raceSummary.append(position).append(". ").append(racer.getName()).append("\n");
            raceSummary.append("   WPM: ").append(format(racer.wpm)).append("\n");
            raceSummary.append("   Accuracy percentage: ").append(format(racer.accuracyPercentage)).append("%\n");
            raceSummary.append("   Burnout count: ").append(racer.burnoutCount).append("\n");
            raceSummary.append("   Accuracy rating change: ")
                .append(format(racer.originalAccuracy)).append(" -> ")
                // Use Typist's getAccuracy() to read the updated value
                .append(format(racer.getAccuracy())).append("\n");
            raceSummary.append("   Personal best WPM: ")
                .append(format(personalBestWpm.get(racer.getName()))).append("\n\n");
        }

        historyArea.append("---\n" + raceSummary.toString()); // append instead of overwrite

        StringBuilder typistHistory = new StringBuilder();
        typistHistory.append("Full Typist History\n\n");
        for (RaceTypist racer : racers)
        {
            typistHistory.append(racer.getName()).append(":\n");
            for (String entry : racer.raceHistory)
            {
                typistHistory.append("  ").append(entry).append("\n");
            }
            typistHistory.append("\n");
        }
        typistHistoryArea.setText(typistHistory.toString());
        refreshLeaderboardArea();
        updateComparisonChoices();
        JOptionPane.showMessageDialog(frame, "And the winner is... " + sorted.get(0).getName() + "!");
    }

    private double calculateWpm(int charactersTyped, double elapsedMinutes)
    {
        double words = charactersTyped / 5.0;
        return words / elapsedMinutes;
    }

    private double calculateAccuracyPercentage(RaceTypist racer)
    {
        if (racer.totalKeystrokes == 0)
        {
            return 0.0;
        }
        return (racer.correctKeystrokes * 100.0) / racer.totalKeystrokes;
    }

    private void updateAccuracyAfterRace(RaceTypist racer, int position)
    {
        if (position == 1)
        {
            // Use Typist's setAccuracy() — handles clamping to [0.0, 1.0] automatically
            racer.setAccuracy(racer.getAccuracy() + 0.02);
        }

        if (racer.burnoutCount > 0)
        {
            racer.setAccuracy(racer.getAccuracy() - (0.01 * racer.burnoutCount));
        }
    }

    private void updatePersonalBest(RaceTypist racer)
    {
        Double previousBest = personalBestWpm.get(racer.getName());
        if (previousBest == null || racer.wpm > previousBest)
        {
            personalBestWpm.put(racer.getName(), racer.wpm);
        }
    }

    private void updateLeaderboard(RaceTypist racer, int position)
    {
        int points = 0;

        if (position == 1)
        {
            points += 3;
        }
        else if (position == 2)
        {
            points += 2;
        }
        else if (position == 3)
        {
            points += 1;
        }

        if (racer.wpm >= 40.0)
        {
            points += 1;
        }
        if (racer.burnoutCount == 0)
        {
            points += 1;
        }
        else
        {
            points -= racer.burnoutCount;
        }

        Integer oldPoints = leaderboardPoints.get(racer.getName());
        if (oldPoints == null)
        {
            oldPoints = 0;
        }
        leaderboardPoints.put(racer.getName(), oldPoints + points);
    }

    private void refreshLeaderboardArea()
    {
        List<String> names = new ArrayList<String>(leaderboardPoints.keySet());
        Collections.sort(names, new Comparator<String>()
        {
            @Override public int compare(String a, String b)
            {
                return leaderboardPoints.get(b) - leaderboardPoints.get(a);
            }
        });

        StringBuilder text = new StringBuilder();
        text.append("Cumulative Leaderboard\n\n");
        for (int i = 0; i < names.size(); i++)
        {
            String name = names.get(i);
            text.append(i + 1).append(". ").append(name)
                .append(" - ").append(leaderboardPoints.get(name)).append(" pts");

            RaceTypist matchedRacer = findRacerByName(name);
            if (matchedRacer != null)
            {
                if (matchedRacer.consecutiveWins >= 3)
                {
                    text.append(" | Badge: Speed Demon");
                }
                if (matchedRacer.racesWithoutBurnout >= 5)
                {
                    text.append(" | Badge: Iron Fingers");
                }
                int points = leaderboardPoints.get(name);
                if (points >= 5 && matchedRacer.consecutiveWins < 3)
                {
                    text.append(" | Badge: Rising Star");
                }
            }
            text.append("\n");
        }
        leaderboardArea.setText(text.toString());
    }

    private void updateComparisonChoices()
    {
        compareTypistOne.removeAllItems();
        compareTypistTwo.removeAllItems();

        for (RaceTypist racer : racers)
        {
            compareTypistOne.addItem(racer.getName());
            compareTypistTwo.addItem(racer.getName());
        }

        if (compareTypistTwo.getItemCount() > 1)
        {
            compareTypistTwo.setSelectedIndex(1);
        }
    }

    private void compareSelectedTypists()
    {
        if (racers.size() < 2)
        {
            comparisonArea.setText("Run a race with at least two typists first.");
            return;
        }

        String firstName = (String) compareTypistOne.getSelectedItem();
        String secondName = (String) compareTypistTwo.getSelectedItem();

        if (firstName != null && firstName.equals(secondName))
        {
            comparisonArea.setText("Please select two different typists to compare.");
            return;
        }

        RaceTypist first = findRacerByName(firstName);
        RaceTypist second = findRacerByName(secondName);

        if (first == null || second == null)
        {
            comparisonArea.setText("Could not compare typists.");
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("Comparison\n\n");
        text.append(first.getName()).append(" vs ").append(second.getName()).append("\n\n");
        text.append("WPM: ").append(format(first.wpm)).append(" vs ").append(format(second.wpm)).append("\n");
        text.append("Accuracy %: ").append(format(first.accuracyPercentage)).append(" vs ").append(format(second.accuracyPercentage)).append("\n");
        text.append("Burnouts: ").append(first.burnoutCount).append(" vs ").append(second.burnoutCount).append("\n");
        text.append("Progress: ").append(first.getProgress()).append(" vs ").append(second.getProgress()).append("\n");
        text.append("Personal best WPM: ")
            .append(format(getBestWpm(first.getName()))).append(" vs ")
            .append(format(getBestWpm(second.getName()))).append("\n");

        text.append("\n--- ").append(first.getName()).append(" Race History ---\n");
        for (String entry : first.raceHistory)
        {
            text.append("  ").append(entry).append("\n");
        }

        text.append("\n--- ").append(second.getName()).append(" Race History ---\n");
        for (String entry : second.raceHistory)
        {
            text.append("  ").append(entry).append("\n");
        }

        comparisonArea.setText(text.toString());
    }

    private RaceTypist findRacerByName(String name)
    {
        for (RaceTypist racer : racers)
        {
            if (racer.getName().equals(name))
            {
                return racer;
            }
        }
        return null;
    }

    private double getBestWpm(String name)
    {
        Double best = personalBestWpm.get(name);
        if (best == null)
        {
            return 0.0;
        }
        return best;
    }

    private void resetRaceDisplay()
    {
        if (raceTimer != null)
        {
            raceTimer.stop();
        }
        raceRunning = false;
        startButton.setEnabled(true);
        lanesPanel.removeAll();
        lanesPanel.revalidate();
        lanesPanel.repaint();

        personalBestWpm.clear();
        leaderboardPoints.clear();
        raceNumber = 0;

        for (RaceTypist racer : racers)
        {
            racer.raceHistory.clear();
            racer.consecutiveWins = 0;
            racer.racesWithoutBurnout = 0;
        }

        historyArea.setText("Race display reset. Configure typists and start a new race.");
        leaderboardArea.setText("Leaderboard will appear here.");
        comparisonArea.setText("Select two typists after a race to compare them.");
        typistHistoryArea.setText("Per-typist history will appear here after a race.");
    }

    private double clamp(double value, double min, double max)
    {
        if (value < min)
        {
            return min;
        }
        if (value > max)
        {
            return max;
        }
        return value;
    }

    private String format(double value)
    {
        return String.format("%.2f", value);
    }

    private Color getColourFromName(String name)
    {
        if ("Blue".equals(name))
        {
            return new Color(70, 130, 180);
        }
        else if ("Green".equals(name))
        {
            return new Color(60, 160, 90);
        }
        else if ("Purple".equals(name))
        {
            return new Color(135, 90, 180);
        }
        else if ("Orange".equals(name))
        {
            return new Color(220, 140, 40);
        }
        else if ("Pink".equals(name))
        {
            return new Color(210, 90, 150);
        }
        else
        {
            return new Color(90, 90, 90);
        }
    }

    // -----------------------------------------------------------------------
    // Inner class: TypistSetupRow — holds all the UI widgets for one typist
    // -----------------------------------------------------------------------

    private class TypistSetupRow
    {
        JPanel panel;
        JTextField nameField;
        JTextField symbolField;
        JComboBox<String> styleBox;
        JComboBox<String> keyboardBox;
        JComboBox<String> colourBox;
        JComboBox<String> accessoryBox;
        double currentRankModifier = 0.0;

        TypistSetupRow(int number, String defaultName, String defaultSymbol)
        {
            panel = new JPanel();
            panel.setLayout(new GridLayout(0, 1, 3, 3));
            panel.setBorder(BorderFactory.createTitledBorder("Typist " + number));

            nameField   = new JTextField(defaultName);
            symbolField = new JTextField(defaultSymbol);

            styleBox = new JComboBox<String>(new String[] {
                "Touch Typist",
                "Hunt & Peck",
                "Phone Thumbs",
                "Voice-to-Text"
            });

            keyboardBox = new JComboBox<String>(new String[] {
                "Mechanical",
                "Membrane",
                "Touchscreen",
                "Stenography"
            });

            colourBox = new JComboBox<String>(new String[] {
                "Blue",
                "Green",
                "Purple",
                "Orange",
                "Pink",
                "Grey"
            });

            accessoryBox = new JComboBox<String>(new String[] {
                "None",
                "Wrist Support",
                "Energy Drink",
                "Noise-Cancelling Headphones"
            });

            panel.add(new JLabel("Name:"));
            panel.add(nameField);
            panel.add(new JLabel("Symbol / emoji:"));
            panel.add(symbolField);
            panel.add(new JLabel("Typing style:"));
            panel.add(styleBox);
            panel.add(new JLabel("Keyboard type:"));
            panel.add(keyboardBox);
            panel.add(new JLabel("Progress colour:"));
            panel.add(colourBox);
            panel.add(new JLabel("Accessory:"));
            panel.add(accessoryBox);
        }
    }

    // -----------------------------------------------------------------------
    // Inner class: RaceTypist extends Typist (Part 1).
    //
    // All core state — progress, burnout flag, burnout countdown, accuracy,
    // name, symbol — lives in the parent Typist class and is accessed only
    // through its public methods (getProgress, isBurntOut, getAccuracy, etc.).
    //
    // RaceTypist adds only the extra fields required by the GUI simulation:
    // speed/burnout multipliers, keystroke counters, stats, and UI components.
    // -----------------------------------------------------------------------

    private class RaceTypist extends Typist
    {
        // GUI / simulation-specific fields not present in Typist
        double originalAccuracy;
        double speedMultiplier;
        double burnoutMultiplier;
        int    slideBackAmount;
        int    burnoutDuration;
        int    burnoutCount;
        int    mistypes;
        int    totalKeystrokes;
        int    correctKeystrokes;
        double wpm;
        double accuracyPercentage;
        boolean finished;
        boolean justMistyped;
        String  style;
        String  keyboard;
        String  accessory;
        List<String> raceHistory;

        // Swing components for this typist's lane panel
        JPanel       lanePanel;
        JLabel       statusLabel;
        JLabel       impactLabel;
        JProgressBar progressBar;
        JTextPane    passagePane;

        // Cross-race tracking for badges and rank modifiers
        int consecutiveWins    = 0;
        int racesWithoutBurnout = 0;

        /**
         * Constructs a RaceTypist, delegating core field initialisation
         * (symbol, name, accuracy, progress, burnout) to Typist's constructor.
         *
         * @param symbol            single character displayed on track
         * @param name              display name
         * @param accuracy          base accuracy in [0.0, 1.0]
         * @param speedMultiplier   scales forward movement probability
         * @param burnoutMultiplier scales burnout probability
         * @param slideBackAmount   characters lost on a mistype
         * @param burnoutDuration   turns spent burnt out
         * @param colour            progress bar colour
         * @param style             typing style label (display only)
         * @param keyboard          keyboard type label (display only)
         * @param accessory         accessory label (display only)
         */
        RaceTypist(char symbol, String name, double accuracy,
                   double speedMultiplier, double burnoutMultiplier,
                   int slideBackAmount, int burnoutDuration,
                   Color colour, String style, String keyboard, String accessory)
        {
            // Call Typist's constructor — sets symbol, name, accuracy, progress=0,
            // burnOut=false, burnoutTurnsRemaining=0
            super(symbol, name, accuracy);

            this.originalAccuracy   = accuracy;
            this.speedMultiplier    = speedMultiplier;
            this.burnoutMultiplier  = burnoutMultiplier;
            this.slideBackAmount    = slideBackAmount;
            this.burnoutDuration    = burnoutDuration;
            this.burnoutCount       = 0;
            this.mistypes           = 0;
            this.totalKeystrokes    = 0;
            this.correctKeystrokes  = 0;
            this.wpm                = 0.0;
            this.accuracyPercentage = 0.0;
            this.finished           = false;
            this.justMistyped       = false;
            this.style              = style;
            this.keyboard           = keyboard;
            this.accessory          = accessory;
            this.raceHistory        = new ArrayList<String>();

            // Build lane UI using Typist's getters for name and symbol
            lanePanel = new JPanel(new BorderLayout(5, 5));
            lanePanel.setBorder(new EmptyBorder(8, 8, 8, 8));

            statusLabel = new JLabel(getSymbol() + " " + getName() + " | ready");
            impactLabel = new JLabel(
                "Style: "    + style
                + " | Keyboard: " + keyboard
                + " | Accessory: " + accessory
                + " | Speed x" + format(speedMultiplier)
                + " | Slide-back " + slideBackAmount
                + " | Burnout " + burnoutDuration + " turns");

            progressBar = new JProgressBar(0, passageLength);
            progressBar.setStringPainted(true);
            progressBar.setForeground(colour);

            passagePane = new JTextPane();
            passagePane.setEditable(false);
            passagePane.setText(currentPassage);
            passagePane.setPreferredSize(new Dimension(0, 40));

            JPanel centerPanel = new JPanel(new BorderLayout(3, 3));
            centerPanel.add(progressBar, BorderLayout.NORTH);
            centerPanel.add(new JScrollPane(passagePane), BorderLayout.CENTER);

            lanePanel.add(statusLabel, BorderLayout.NORTH);
            lanePanel.add(centerPanel, BorderLayout.CENTER);
            lanePanel.add(impactLabel, BorderLayout.SOUTH);
        }
    }
}