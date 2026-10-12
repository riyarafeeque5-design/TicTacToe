import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Random;

/**
 * Tic-Tac-Toe - full-screen game (v5)
 * Pages: Start page -> Setup page (mode, names, rounds) -> Game page.
 * Everything (popups, name entry, exit box) uses the game's own design - no default Java dialogs.
 * Opens maximised and scales with your screen. 5 themes, sounds, final winner screen.
 */

// =============== MODULE 1: Game logic (no GUI code here) ===============
class Board {
    private final char[][] cells = new char[3][3];

    public Board() { reset(); }

    public void reset() {
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                cells[r][c] = ' ';
    }

    public boolean place(int r, int c, char mark) {
        if (cells[r][c] != ' ') return false;
        cells[r][c] = mark;
        return true;
    }

    public void clear(int r, int c) { cells[r][c] = ' '; }

    public char get(int r, int c) { return cells[r][c]; }

    /** The 3 winning cells {{r,c},{r,c},{r,c}} or null if nobody has won */
    public int[][] getWinLine() {
        for (int i = 0; i < 3; i++) {
            if (cells[i][0] != ' ' && cells[i][0] == cells[i][1] && cells[i][1] == cells[i][2])
                return new int[][]{{i, 0}, {i, 1}, {i, 2}};
            if (cells[0][i] != ' ' && cells[0][i] == cells[1][i] && cells[1][i] == cells[2][i])
                return new int[][]{{0, i}, {1, i}, {2, i}};
        }
        if (cells[1][1] != ' ') {
            if (cells[0][0] == cells[1][1] && cells[1][1] == cells[2][2])
                return new int[][]{{0, 0}, {1, 1}, {2, 2}};
            if (cells[0][2] == cells[1][1] && cells[1][1] == cells[2][0])
                return new int[][]{{0, 2}, {1, 1}, {2, 0}};
        }
        return null;
    }

    public char getWinner() {
        int[][] line = getWinLine();
        return line == null ? ' ' : cells[line[0][0]][line[0][1]];
    }

    public boolean isFull() {
        for (char[] row : cells)
            for (char ch : row)
                if (ch == ' ') return false;
        return true;
    }

    public ArrayList<int[]> freeCells() {
        ArrayList<int[]> list = new ArrayList<>();
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                if (cells[r][c] == ' ') list.add(new int[]{r, c});
        return list;
    }
}

// =============== MODULE 2: Computer player ===============
class ComputerPlayer {
    private final Random random = new Random();

    public int[] chooseMove(Board board) {
        int[] move = findWinningMove(board, 'O');          // 1. win
        if (move != null) return move;
        move = findWinningMove(board, 'X');                // 2. block
        if (move != null) return move;
        if (board.get(1, 1) == ' ') return new int[]{1, 1};   // 3. centre

        ArrayList<int[]> corners = new ArrayList<>();      // 4. corner
        int[][] all = {{0, 0}, {0, 2}, {2, 0}, {2, 2}};
        for (int[] p : all)
            if (board.get(p[0], p[1]) == ' ') corners.add(p);
        if (!corners.isEmpty()) return corners.get(random.nextInt(corners.size()));

        ArrayList<int[]> free = board.freeCells();         // 5. anything
        return free.get(random.nextInt(free.size()));
    }

    private int[] findWinningMove(Board board, char mark) {
        for (int[] p : board.freeCells()) {
            board.place(p[0], p[1], mark);
            boolean wins = board.getWinner() == mark;
            board.clear(p[0], p[1]);
            if (wins) return p;
        }
        return null;
    }
}

// =============== MODULE 3: Sound effects ===============
/** Generates simple beeps / jingles in code - no sound files needed */
class SoundFX {
    static boolean enabled = true;
    private static final float RATE = 44100f;

    /** Play notes one after another. freq 0 = silence. */
    static void play(int[] freqs, int[] ms, double volume) {
        if (!enabled) return;
        final byte[] data = synth(freqs, ms, volume);
        Thread th = new Thread(() -> {
            SourceDataLine line = null;
            try {
                AudioFormat af = new AudioFormat(RATE, 16, 1, true, false);
                line = AudioSystem.getSourceDataLine(af);
                line.open(af);
                line.start();
                line.write(data, 0, data.length);
                line.drain();
            } catch (Exception ignored) {
                // no sound device - just stay silent
            } finally {
                if (line != null) line.close();
            }
        });
        th.setDaemon(true);
        th.start();
    }

    private static byte[] synth(int[] freqs, int[] ms, double volume) {
        int total = 0;
        for (int m : ms) total += (int) (RATE * m / 1000);
        byte[] out = new byte[total * 2];
        int pos = 0;
        for (int k = 0; k < freqs.length; k++) {
            int n = (int) (RATE * ms[k] / 1000);
            double f = freqs[k];
            for (int i = 0; i < n; i++) {
                double t = i / RATE;
                double attack = Math.min(1.0, i / (RATE * 0.005));
                double release = Math.min(1.0, (n - i) / (RATE * 0.03));
                double body = 0.6 + 0.4 * Math.exp(-4.0 * i / n);
                double v = 0;
                if (f > 0) {
                    v = Math.sin(2 * Math.PI * f * t) * 0.75
                            + Math.sin(4 * Math.PI * f * t) * 0.20
                            + Math.sin(6 * Math.PI * f * t) * 0.05;
                }
                short s = (short) (v * attack * release * body * volume * 32767);
                out[pos++] = (byte) (s & 0xFF);
                out[pos++] = (byte) ((s >> 8) & 0xFF);
            }
        }
        return out;
    }

    // ---- the actual game sounds ----
    static void click()     { play(new int[]{880}, new int[]{35}, 0.30); }
    static void button()    { play(new int[]{660, 880}, new int[]{45, 60}, 0.35); }
    static void place(char m) {
        if (m == 'X') play(new int[]{700}, new int[]{75}, 0.40);
        else          play(new int[]{520}, new int[]{75}, 0.40);
    }
    static void taken()     { play(new int[]{180}, new int[]{120}, 0.40); }
    static void start()     { play(new int[]{392, 523, 659, 784, 1047}, new int[]{80, 80, 80, 80, 260}, 0.45); }
    static void roundWin()  { play(new int[]{523, 659, 784, 1047}, new int[]{110, 110, 110, 300}, 0.45); }
    static void roundDraw() { play(new int[]{392, 330, 262}, new int[]{140, 140, 260}, 0.40); }
    static void finalWin()  {
        play(new int[]{523, 659, 784, 1047, 784, 1047, 1319},
                new int[]{130, 130, 130, 260, 110, 130, 700}, 0.50);
    }
    static void finalTie()  {
        play(new int[]{392, 392, 349, 330, 262}, new int[]{150, 150, 150, 150, 450}, 0.45);
    }
}

// =============== MODULE 4: Custom design components ===============

/** Page background: soft top-to-bottom gradient */
class GradientPanel extends JPanel {
    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, TicTacToe.BG_TOP, 0, getHeight(), TicTacToe.BG_BOTTOM));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }
}

/** Rounded panel (score cards, status pill, popups) with optional glowing border */
class RoundPanel extends JPanel {
    private Color bg;
    private Color border = null;
    private final int arc;

    RoundPanel(Color bg, int arc) {
        this.bg = bg;
        this.arc = arc;
        setOpaque(false);
    }

    void setBg(Color c) { bg = c; repaint(); }

    void setBorderColor(Color c) { border = c; repaint(); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
        if (border != null) {
            g2.setStroke(new BasicStroke(2.2f));
            g2.setColor(border);
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

/** Rounded gradient button (plays a click sound) */
class PillButton extends JButton {
    private Color base;

    PillButton(String text, Color base) {
        super(text);
        this.base = base;
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setForeground(new Color(0x10, 0x12, 0x22));
        setFont(TicTacToe.font(Font.BOLD, 15));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(TicTacToe.sc(150), TicTacToe.sc(44)));
        addActionListener(e -> SoundFX.button());
    }

    void setBase(Color c) { base = c; repaint(); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color c = getModel().isPressed() ? base.darker()
                : getModel().isRollover() ? TicTacToe.lighten(base, 25) : base;
        g2.setPaint(new GradientPaint(0, 0, TicTacToe.lighten(c, 30), 0, getHeight(), c));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}

/** Selectable option button (mode / number of rounds) */
class ChoiceButton extends JButton {
    private boolean chosen = false;

    ChoiceButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(TicTacToe.font(Font.BOLD, 16));
        setPreferredSize(new Dimension(TicTacToe.sc(160), TicTacToe.sc(52)));
        addActionListener(e -> SoundFX.click());
        setChosen(false);
    }

    void setChosen(boolean c) {
        chosen = c;
        setForeground(c ? new Color(0x10, 0x12, 0x22) : TicTacToe.TEXT);
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        if (chosen) {
            g2.setPaint(new GradientPaint(0, 0, TicTacToe.lighten(TicTacToe.X_COLOR, 30), 0, h, TicTacToe.X_COLOR));
            g2.fillRoundRect(0, 0, w, h, h, h);
        } else {
            g2.setColor(getModel().isRollover() ? TicTacToe.CARD_ON : TicTacToe.CARD);
            g2.fillRoundRect(0, 0, w, h, h, h);
            Color t = TicTacToe.TEXT;
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(t.getRed(), t.getGreen(), t.getBlue(), 50));
            g2.drawRoundRect(1, 1, w - 3, h - 3, h - 2, h - 2);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

/** Rounded text box for player names */
class NameField extends JTextField {
    private final String hint;

    NameField(String hint) {
        this.hint = hint;
        setOpaque(false);
        setBorder(new EmptyBorder(TicTacToe.sc(8), TicTacToe.sc(22), TicTacToe.sc(8), TicTacToe.sc(22)));
        setFont(TicTacToe.font(Font.BOLD, 18));
        setPreferredSize(new Dimension(TicTacToe.sc(340), TicTacToe.sc(54)));
        setMaximumSize(new Dimension(TicTacToe.sc(340), TicTacToe.sc(54)));
        setHorizontalAlignment(SwingConstants.CENTER);
        refreshTheme();
        addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) { repaint(); }
            @Override public void focusLost(FocusEvent e) { repaint(); }
        });
    }

    void refreshTheme() {
        setForeground(TicTacToe.TEXT);
        setCaretColor(TicTacToe.TEXT);
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setColor(TicTacToe.CARD);
        g2.fillRoundRect(0, 0, w, h, h, h);
        Color t = TicTacToe.TEXT;
        g2.setStroke(new BasicStroke(2.2f));
        g2.setColor(hasFocus() ? TicTacToe.X_COLOR : new Color(t.getRed(), t.getGreen(), t.getBlue(), 50));
        g2.drawRoundRect(1, 1, w - 3, h - 3, h - 2, h - 2);
        g2.dispose();

        super.paintComponent(g);

        if (getText().isEmpty() && !hasFocus()) {
            Graphics2D hg = (Graphics2D) g.create();
            hg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            hg.setFont(getFont());
            hg.setColor(TicTacToe.MUTED);
            FontMetrics fm = hg.getFontMetrics();
            int x = (w - fm.stringWidth(hint)) / 2;
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            hg.drawString(hint, x, y);
            hg.dispose();
        }
    }
}

/** Keeps its child a centred square (so the board is always square, whatever the window size) */
class SquarePanel extends JPanel {
    private final Component child;

    SquarePanel(Component child) {
        this.child = child;
        setOpaque(false);
        setLayout(null);
        add(child);
    }

    @Override public void doLayout() {
        int s = Math.min(Math.min(getWidth(), getHeight()), TicTacToe.sc(640));
        child.setBounds((getWidth() - s) / 2, (getHeight() - s) / 2, s, s);
    }

    @Override public Dimension getPreferredSize() {
        return new Dimension(TicTacToe.sc(420), TicTacToe.sc(420));
    }
}

/** Board square: draws X / O as vector shapes with a small draw-in animation */
class CellButton extends JButton {
    private boolean highlight = false;
    private char mark = ' ';
    private float progress = 1f;
    private javax.swing.Timer anim;

    CellButton() {
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    void setHighlight(boolean h) { highlight = h; repaint(); }

    /** ' ' clears the square, 'X' / 'O' draws the mark with animation */
    void setMark(char m) {
        mark = m;
        if (anim != null) anim.stop();
        progress = 0f;
        if (m == ' ') { repaint(); return; }
        anim = new javax.swing.Timer(15, e -> {
            progress += 0.09f;
            if (progress >= 1f) { progress = 1f; anim.stop(); }
            repaint();
        });
        anim.start();
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int w = getWidth(), h = getHeight() - 5;
        int arc = Math.max(20, w / 5);

        // soft shadow + body
        g2.setColor(new Color(0, 0, 0, 80));
        g2.fillRoundRect(2, 6, w - 4, h, arc, arc);
        Color body = highlight ? TicTacToe.WIN
                : (getModel().isRollover() && mark == ' ') ? TicTacToe.CELL_HOVER : TicTacToe.CELL;
        g2.setPaint(new GradientPaint(0, 0, TicTacToe.lighten(body, 14), 0, h, body));
        g2.fillRoundRect(0, 0, w, h, arc, arc);

        // the mark
        if (mark != ' ') {
            float p = 1f - (1f - progress) * (1f - progress);     // ease-out
            int size = Math.min(w, h);
            double cx = w / 2.0, cy = h / 2.0, d = size / 4.0;
            Color mc = highlight ? Color.WHITE : (mark == 'X' ? TicTacToe.X_COLOR : TicTacToe.O_COLOR);

            for (int pass = 0; pass < 2; pass++) {                // pass 0 = glow, pass 1 = solid
                float width = size / 9f * (pass == 0 ? 1.9f : 1f);
                g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(pass == 0 ? new Color(mc.getRed(), mc.getGreen(), mc.getBlue(), 55) : mc);
                if (mark == 'X') {
                    g2.draw(new Line2D.Double(cx - d, cy - d, cx - d + 2 * d * p, cy - d + 2 * d * p));
                    g2.draw(new Line2D.Double(cx + d, cy - d, cx + d - 2 * d * p, cy - d + 2 * d * p));
                } else {
                    g2.draw(new Arc2D.Double(cx - d, cy - d, 2 * d, 2 * d, 90, -360 * p, Arc2D.OPEN));
                }
            }
        }
        g2.dispose();
    }
}

// =============== MODULE 5: Start page ===============

/** Small game logo: a 3x3 grid with a winning diagonal of X's */
class LogoIcon extends JComponent {
    LogoIcon() {
        Dimension d = new Dimension(TicTacToe.sc(190), TicTacToe.sc(190));
        setPreferredSize(d);
        setMaximumSize(d);
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float k = (float) TicTacToe.SCALE;
        int s = Math.min(getWidth(), getHeight());
        int pad = Math.round(10 * k);
        int cell = (s - 2 * pad) / 3;

        // grid lines
        Color t = TicTacToe.TEXT;
        g2.setStroke(new BasicStroke(6f * k, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(t.getRed(), t.getGreen(), t.getBlue(), 150));
        for (int i = 1; i <= 2; i++) {
            g2.drawLine(pad + i * cell, pad, pad + i * cell, pad + 3 * cell);
            g2.drawLine(pad, pad + i * cell, pad + 3 * cell, pad + i * cell);
        }

        // marks: X on the diagonal, O in two corners
        double d = cell * 0.27;
        g2.setStroke(new BasicStroke(8f * k, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            double cx = pad + i * cell + cell / 2.0, cy = pad + i * cell + cell / 2.0;
            g2.setColor(TicTacToe.X_COLOR);
            g2.draw(new Line2D.Double(cx - d, cy - d, cx + d, cy + d));
            g2.draw(new Line2D.Double(cx + d, cy - d, cx - d, cy + d));
        }
        g2.setColor(TicTacToe.O_COLOR);
        double ox1 = pad + 2 * cell + cell / 2.0, oy1 = pad + cell / 2.0;
        double ox2 = pad + cell / 2.0, oy2 = pad + 2 * cell + cell / 2.0;
        g2.draw(new Ellipse2D.Double(ox1 - d, oy1 - d, 2 * d, 2 * d));
        g2.draw(new Ellipse2D.Double(ox2 - d, oy2 - d, 2 * d, 2 * d));
        g2.dispose();
    }
}

/** First page: animated background, logo, title and menu buttons */
class StartScreen extends JPanel {
    private static final int N = 26;
    private final Random rnd = new Random();
    private final float[] fx = new float[N], fy = new float[N], fs = new float[N],
            fv = new float[N], fr = new float[N], fvr = new float[N];
    private final boolean[] isX = new boolean[N];
    private boolean spawned = false;
    private double phase = 0;
    private javax.swing.Timer timer;

    private final JLabel titleLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel subLabel = new JLabel("Classic game  -  2 Players or vs Computer", SwingConstants.CENTER);
    private final LogoIcon logo = new LogoIcon();
    private final PillButton startBtn, howBtn, themeBtn, soundBtn, exitBtn;

    StartScreen(Runnable onStart, Runnable onHow, Runnable onTheme, Runnable onSound, Runnable onExit) {
        setLayout(new GridBagLayout());

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setFont(TicTacToe.font(Font.BOLD, 46));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subLabel.setFont(TicTacToe.font(Font.PLAIN, 15));
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        startBtn = new PillButton("START GAME", TicTacToe.X_COLOR);
        startBtn.setFont(TicTacToe.font(Font.BOLD, 22));
        size(startBtn, 300, 64);
        howBtn = new PillButton("How to Play", TicTacToe.MUTED);
        themeBtn = new PillButton("Theme", TicTacToe.MUTED);
        soundBtn = new PillButton("Sound: ON", TicTacToe.MUTED);
        exitBtn = new PillButton("Exit", TicTacToe.O_COLOR);
        size(howBtn, 270, 46);
        size(themeBtn, 270, 46);
        size(soundBtn, 270, 46);
        size(exitBtn, 270, 46);

        startBtn.addActionListener(e -> onStart.run());
        howBtn.addActionListener(e -> onHow.run());
        themeBtn.addActionListener(e -> onTheme.run());
        soundBtn.addActionListener(e -> onSound.run());
        exitBtn.addActionListener(e -> onExit.run());

        box.add(logo);
        box.add(Box.createVerticalStrut(TicTacToe.sc(8)));
        box.add(titleLabel);
        box.add(subLabel);
        box.add(Box.createVerticalStrut(TicTacToe.sc(26)));
        box.add(startBtn);
        box.add(Box.createVerticalStrut(TicTacToe.sc(18)));
        box.add(howBtn);
        box.add(Box.createVerticalStrut(TicTacToe.sc(10)));
        box.add(themeBtn);
        box.add(Box.createVerticalStrut(TicTacToe.sc(10)));
        box.add(soundBtn);
        box.add(Box.createVerticalStrut(TicTacToe.sc(10)));
        box.add(exitBtn);
        add(box);

        refreshTheme(TicTacToe.THEME_NAMES[0]);
    }

    private void size(PillButton b, int w, int h) {
        Dimension d = new Dimension(TicTacToe.sc(w), TicTacToe.sc(h));
        b.setPreferredSize(d);
        b.setMaximumSize(d);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    void setSoundText(boolean on) { soundBtn.setText("Sound: " + (on ? "ON" : "OFF")); }

    void refreshTheme(String themeName) {
        titleLabel.setText(TicTacToe.titleHtml());
        subLabel.setForeground(TicTacToe.MUTED);
        startBtn.setBase(TicTacToe.X_COLOR);
        howBtn.setBase(TicTacToe.MUTED);
        themeBtn.setBase(TicTacToe.MUTED);
        soundBtn.setBase(TicTacToe.MUTED);
        exitBtn.setBase(TicTacToe.O_COLOR);
        themeBtn.setText("Theme: " + themeName);
        repaint();
    }

    /** Start / stop the background animation (only needed while this page is visible) */
    void setRunning(boolean run) {
        if (timer != null) timer.stop();
        if (!run) return;
        timer = new javax.swing.Timer(30, e -> tick());
        timer.start();
    }

    private void tick() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float k = (float) TicTacToe.SCALE;
        if (!spawned) {
            for (int i = 0; i < N; i++) {
                fx[i] = rnd.nextFloat() * w;
                fy[i] = rnd.nextFloat() * h;
                fs[i] = (24f + rnd.nextFloat() * 40f) * k;
                fv[i] = (0.3f + rnd.nextFloat() * 0.8f) * k;
                fr[i] = rnd.nextFloat() * 6.28f;
                fvr[i] = (rnd.nextFloat() - 0.5f) * 0.03f;
                isX[i] = rnd.nextBoolean();
            }
            spawned = true;
        }
        for (int i = 0; i < N; i++) {
            fy[i] -= fv[i];
            fr[i] += fvr[i];
            if (fy[i] < -fs[i]) {
                fy[i] = h + fs[i];
                fx[i] = rnd.nextFloat() * w;
            }
        }
        // pulsing START button
        phase += 0.12;
        double q = (Math.sin(phase) + 1) / 2;
        Color a = TicTacToe.X_COLOR, b = TicTacToe.lighten(TicTacToe.X_COLOR, 45);
        startBtn.setBase(new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * q),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * q),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * q)));
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, TicTacToe.BG_TOP, 0, getHeight(), TicTacToe.BG_BOTTOM));
        g2.fillRect(0, 0, getWidth(), getHeight());

        if (spawned) {
            for (int i = 0; i < N; i++) {
                Graphics2D c = (Graphics2D) g2.create();
                Color mc = isX[i] ? TicTacToe.X_COLOR : TicTacToe.O_COLOR;
                c.setColor(new Color(mc.getRed(), mc.getGreen(), mc.getBlue(), 45));
                c.setStroke(new BasicStroke(fs[i] / 6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                c.translate(fx[i], fy[i]);
                c.rotate(fr[i]);
                float d = fs[i] / 2f;
                if (isX[i]) {
                    c.draw(new Line2D.Float(-d, -d, d, d));
                    c.draw(new Line2D.Float(d, -d, -d, d));
                } else {
                    c.draw(new Ellipse2D.Float(-d, -d, 2 * d, 2 * d));
                }
                c.dispose();
            }
        }
        g2.dispose();
    }
}

// =============== MODULE 6: Setup page - mode, player names, rounds (NEW) ===============
class SetupScreen extends GradientPanel {
    private boolean vsComputer = false;
    private int rounds = 3;

    private final JLabel titleLabel = new JLabel("GAME SETUP", SwingConstants.CENTER);
    private final JLabel modeHead, p1Head, p2Head, compHead, roundsHead;
    private final ChoiceButton twoBtn = new ChoiceButton("2 Players");
    private final ChoiceButton compBtn = new ChoiceButton("Vs Computer");
    private final ChoiceButton r3 = new ChoiceButton("3 Rounds");
    private final ChoiceButton r5 = new ChoiceButton("5 Rounds");
    private final ChoiceButton r10 = new ChoiceButton("10 Rounds");
    private final NameField f1 = new NameField("Player 1");
    private final NameField f2 = new NameField("Player 2");
    private final PillButton playBtn = new PillButton("PLAY", TicTacToe.X_COLOR);
    private final PillButton backBtn = new PillButton("Back", TicTacToe.MUTED);

    SetupScreen(Runnable onPlay, Runnable onBack) {
        setLayout(new GridBagLayout());

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));

        titleLabel.setFont(TicTacToe.font(Font.BOLD, 38));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        modeHead = head("CHOOSE MODE");
        p1Head = head("PLAYER 1  (X)");
        p2Head = head("PLAYER 2  (O)");
        compHead = head("OPPONENT:  COMPUTER  (O)");
        roundsHead = head("NUMBER OF ROUNDS");

        f1.setAlignmentX(Component.CENTER_ALIGNMENT);
        f2.setAlignmentX(Component.CENTER_ALIGNMENT);

        twoBtn.addActionListener(e -> { vsComputer = false; applyChoices(); });
        compBtn.addActionListener(e -> { vsComputer = true; applyChoices(); });
        r3.addActionListener(e -> { rounds = 3; applyChoices(); });
        r5.addActionListener(e -> { rounds = 5; applyChoices(); });
        r10.addActionListener(e -> { rounds = 10; applyChoices(); });

        Dimension pd = new Dimension(TicTacToe.sc(300), TicTacToe.sc(60));
        playBtn.setFont(TicTacToe.font(Font.BOLD, 22));
        playBtn.setPreferredSize(pd);
        playBtn.setMaximumSize(pd);
        playBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        Dimension bd = new Dimension(TicTacToe.sc(200), TicTacToe.sc(44));
        backBtn.setPreferredSize(bd);
        backBtn.setMaximumSize(bd);
        backBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        playBtn.addActionListener(e -> onPlay.run());
        backBtn.addActionListener(e -> onBack.run());
        f1.addActionListener(e -> f2.requestFocusInWindow());
        f2.addActionListener(e -> playBtn.doClick());

        box.add(titleLabel);
        box.add(Box.createVerticalStrut(TicTacToe.sc(22)));
        box.add(modeHead);
        box.add(Box.createVerticalStrut(TicTacToe.sc(6)));
        box.add(row(twoBtn, compBtn));
        box.add(Box.createVerticalStrut(TicTacToe.sc(20)));
        box.add(p1Head);
        box.add(Box.createVerticalStrut(TicTacToe.sc(6)));
        box.add(f1);
        box.add(Box.createVerticalStrut(TicTacToe.sc(16)));
        box.add(p2Head);
        box.add(Box.createVerticalStrut(TicTacToe.sc(6)));
        box.add(f2);
        box.add(compHead);
        box.add(Box.createVerticalStrut(TicTacToe.sc(20)));
        box.add(roundsHead);
        box.add(Box.createVerticalStrut(TicTacToe.sc(6)));
        box.add(row(r3, r5, r10));
        box.add(Box.createVerticalStrut(TicTacToe.sc(26)));
        box.add(playBtn);
        box.add(Box.createVerticalStrut(TicTacToe.sc(10)));
        box.add(backBtn);
        add(box);

        refreshTheme();
    }

    private JLabel head(String text) {
        JLabel l = new JLabel(text, SwingConstants.CENTER);
        l.setFont(TicTacToe.font(Font.BOLD, 13));
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        return l;
    }

    private JPanel row(JComponent... items) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, TicTacToe.sc(12), 0));
        p.setOpaque(false);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (JComponent c : items) p.add(c);
        return p;
    }

    private void applyChoices() {
        twoBtn.setChosen(!vsComputer);
        compBtn.setChosen(vsComputer);
        r3.setChosen(rounds == 3);
        r5.setChosen(rounds == 5);
        r10.setChosen(rounds == 10);
        p2Head.setVisible(!vsComputer);
        f2.setVisible(!vsComputer);
        compHead.setVisible(vsComputer);
        revalidate();
        repaint();
    }

    void refreshTheme() {
        titleLabel.setForeground(TicTacToe.TEXT);
        modeHead.setForeground(TicTacToe.MUTED);
        roundsHead.setForeground(TicTacToe.MUTED);
        p1Head.setForeground(TicTacToe.X_COLOR);
        p2Head.setForeground(TicTacToe.O_COLOR);
        compHead.setForeground(TicTacToe.O_COLOR);
        f1.refreshTheme();
        f2.refreshTheme();
        playBtn.setBase(TicTacToe.X_COLOR);
        backBtn.setBase(TicTacToe.MUTED);
        applyChoices();
    }

    /** Called when the page is opened */
    void prepare() {
        applyChoices();
        SwingUtilities.invokeLater(() -> f1.requestFocusInWindow());
    }

    boolean isVsComputer() { return vsComputer; }

    int rounds() { return rounds; }

    String name1() { return clean(f1.getText(), "Player 1"); }

    String name2() { return vsComputer ? "Computer" : clean(f2.getText(), "Player 2"); }

    private String clean(String s, String def) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) return def;
        return s.length() > 12 ? s.substring(0, 12) : s;
    }
}

// =============== MODULE 7: Popups + final winner screen ===============

/** A gold (or silver) trophy drawn with Java2D shapes */
class TrophyIcon extends JComponent {
    private Color color = new Color(0xFFC107);

    TrophyIcon() {
        Dimension d = new Dimension(TicTacToe.sc(130), TicTacToe.sc(115));
        setPreferredSize(d);
        setMaximumSize(d);
    }

    void setColor(Color c) { color = c; repaint(); }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double k = TicTacToe.SCALE;
        g2.scale(k, k);
        int cx = (int) (getWidth() / k / 2);
        Color dark = color.darker();

        // handles
        g2.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(color);
        g2.draw(new Arc2D.Double(cx - 52, 16, 34, 34, 90, 180, Arc2D.OPEN));
        g2.draw(new Arc2D.Double(cx + 18, 16, 34, 34, 90, -180, Arc2D.OPEN));

        // cup
        Path2D cup = new Path2D.Double();
        cup.moveTo(cx - 34, 8);
        cup.lineTo(cx + 34, 8);
        cup.curveTo(cx + 34, 58, cx + 14, 70, cx, 70);
        cup.curveTo(cx - 14, 70, cx - 34, 58, cx - 34, 8);
        cup.closePath();
        g2.setPaint(new GradientPaint(cx - 34, 0, TicTacToe.lighten(color, 40), cx + 34, 0, dark));
        g2.fill(cup);

        // shine on the cup
        g2.setColor(new Color(255, 255, 255, 90));
        g2.fillRoundRect(cx - 24, 16, 8, 34, 8, 8);

        // stem + base
        g2.setColor(dark);
        g2.fillRect(cx - 5, 70, 10, 20);
        g2.setColor(color);
        g2.fillRoundRect(cx - 30, 88, 60, 12, 8, 8);
        g2.setColor(dark);
        g2.fillRoundRect(cx - 22, 100, 44, 9, 6, 6);
        g2.dispose();
    }
}

/**
 * Full-window overlay (used as the glass pane). Shows every popup in the game's own design:
 * round results, exit box, how-to-play, and the final winner screen (trophy + confetti).
 */
class Overlay extends JPanel {
    private static final int N = 120;
    private final Random rnd = new Random();
    private final TrophyIcon trophy = new TrophyIcon();
    private final JLabel headingLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel line1 = new JLabel("", SwingConstants.CENTER);
    private final JLabel line2 = new JLabel("", SwingConstants.CENTER);
    private final JLabel line3 = new JLabel("", SwingConstants.CENTER);
    private final RoundPanel card = new RoundPanel(TicTacToe.CARD, TicTacToe.sc(44));
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, TicTacToe.sc(14), 0));

    private javax.swing.Timer timer;
    private boolean confetti = false;
    private boolean spawned = false;
    private Color[] palette = new Color[0];
    private final float[] px = new float[N], py = new float[N], pvx = new float[N], pvy = new float[N],
            pr = new float[N], pvr = new float[N];
    private final int[] pc = new int[N];

    Overlay() {
        setOpaque(false);
        setLayout(new GridBagLayout());

        // swallow mouse events so the page underneath can't be clicked
        MouseAdapter block = new MouseAdapter() { };
        addMouseListener(block);
        addMouseMotionListener(block);
        addMouseWheelListener(block);

        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(TicTacToe.sc(24), TicTacToe.sc(40), TicTacToe.sc(28), TicTacToe.sc(40)));

        trophy.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (JLabel l : new JLabel[]{headingLabel, line1, line2, line3})
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
        headingLabel.setFont(TicTacToe.font(Font.BOLD, 32));
        line2.setFont(TicTacToe.font(Font.BOLD, 17));
        line3.setFont(TicTacToe.font(Font.PLAIN, 14));

        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(trophy);
        card.add(Box.createVerticalStrut(TicTacToe.sc(6)));
        card.add(headingLabel);
        card.add(Box.createVerticalStrut(TicTacToe.sc(8)));
        card.add(line1);
        card.add(Box.createVerticalStrut(TicTacToe.sc(14)));
        card.add(line2);
        card.add(Box.createVerticalStrut(TicTacToe.sc(4)));
        card.add(line3);
        card.add(Box.createVerticalStrut(TicTacToe.sc(22)));
        card.add(buttons);
        add(card);
    }

    /** Normal popup: heading + up to 3 lines + buttons. Optional trophy and confetti. */
    void show(boolean withTrophy, boolean withConfetti, String heading, String l1, String l2, String l3,
              Color accent, String[] labels, Color[] colors, Runnable[] actions) {
        line1.setFont(TicTacToe.font(Font.BOLD, 22));
        fill(heading, l1, l2, l3, accent, withTrophy);
        setButtons(labels, colors, actions);
        begin(withConfetti, accent);
    }

    /** Info popup with a longer (HTML) body text and one button */
    void showInfo(String heading, String htmlBody, Color accent, String label, Runnable action) {
        line1.setFont(TicTacToe.font(Font.PLAIN, 16));
        fill(heading, htmlBody, null, null, accent, false);
        setButtons(new String[]{label}, new Color[]{TicTacToe.X_COLOR}, new Runnable[]{action});
        begin(false, accent);
    }

    private void fill(String heading, String l1, String l2, String l3, Color accent, boolean withTrophy) {
        card.setBg(TicTacToe.CARD);
        card.setBorderColor(accent);
        trophy.setVisible(withTrophy);
        trophy.setColor(accent);
        headingLabel.setText(heading);
        headingLabel.setForeground(accent);
        setLine(line1, l1, TicTacToe.TEXT);
        setLine(line2, l2, TicTacToe.TEXT);
        setLine(line3, l3, TicTacToe.MUTED);
    }

    private void setLine(JLabel l, String text, Color c) {
        l.setText(text == null ? "" : text);
        l.setForeground(c);
        l.setVisible(text != null && !text.isEmpty());
    }

    private void setButtons(String[] labels, Color[] colors, Runnable[] actions) {
        buttons.removeAll();
        for (int i = 0; i < labels.length; i++) {
            PillButton b = new PillButton(labels[i], colors[i]);
            final Runnable act = actions[i];
            b.addActionListener(e -> {
                hideOverlay();
                if (act != null) act.run();
            });
            buttons.add(b);
        }
    }

    private void begin(boolean withConfetti, Color accent) {
        confetti = withConfetti;
        spawned = false;
        if (timer != null) timer.stop();
        setVisible(true);
        revalidate();
        repaint();
        if (withConfetti) {
            palette = new Color[]{TicTacToe.X_COLOR, TicTacToe.O_COLOR, TicTacToe.WIN,
                    new Color(0xFFD54F), Color.WHITE, accent};
            timer = new javax.swing.Timer(25, e -> tick());
            timer.start();
        }
    }

    void hideOverlay() {
        if (timer != null) timer.stop();
        confetti = false;
        setVisible(false);
    }

    private void tick() {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float k = (float) TicTacToe.SCALE;
        if (!spawned) {
            for (int i = 0; i < N; i++) {
                px[i] = rnd.nextFloat() * w;
                py[i] = -rnd.nextFloat() * h;
                pvy[i] = (2f + rnd.nextFloat() * 3f) * k;
                pvx[i] = (rnd.nextFloat() - 0.5f) * 1.5f;
                pr[i] = rnd.nextFloat() * 6.28f;
                pvr[i] = (rnd.nextFloat() - 0.5f) * 0.3f;
                pc[i] = rnd.nextInt(palette.length);
            }
            spawned = true;
        }
        for (int i = 0; i < N; i++) {
            py[i] += pvy[i];
            px[i] += pvx[i];
            pr[i] += pvr[i];
            if (py[i] > h + 10) {
                py[i] = -10;
                px[i] = rnd.nextFloat() * w;
            }
        }
        repaint();
    }

    @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(8, 10, 25, 205));
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (confetti && spawned) {
            double k = TicTacToe.SCALE;
            for (int i = 0; i < N; i++) {
                Graphics2D c = (Graphics2D) g2.create();
                c.setColor(palette[pc[i]]);
                c.translate(px[i], py[i]);
                c.rotate(pr[i]);
                c.scale(k, k);
                c.fillRect(-5, -3, 10, 6);
                c.dispose();
            }
        }
        g2.dispose();
    }
}

// =============== MODULE 8: Game window + game flow ===============
public class TicTacToe extends JFrame {
    // ---- Screen scaling: everything grows on bigger screens ----
    static final double SCALE = computeScale();

    private static double computeScale() {
        try {
            double h = Toolkit.getDefaultToolkit().getScreenSize().height;
            return Math.max(0.85, Math.min(1.7, h / 800.0));
        } catch (Throwable t) {
            return 1.0;
        }
    }

    static int sc(int v) { return (int) Math.round(v * SCALE); }

    static Font font(int style, int size) { return new Font("Segoe UI", style, sc(size)); }

    // ---- Themes: {top, bottom, card, cardOn, cell, cellHover, win, X, O, text, muted} ----
    static final String[] THEME_NAMES = {"Neon Night", "Ocean", "Sunset", "Forest", "Candy"};
    static final int[][] THEMES = {
            {0x0F1226, 0x261F55, 0x23264A, 0x34396E, 0x2B2F58, 0x3A407A, 0x1EA868, 0x4FC3F7, 0xFF6B81, 0xE8EAF6, 0x9AA0C8},
            {0x041C32, 0x064663, 0x0B3A5A, 0x145A85, 0x0E4A70, 0x17688F, 0x1EB980, 0xFFD54F, 0xFF8A65, 0xE0F7FA, 0x80CBC4},
            {0x2B0A3D, 0x8E3B46, 0x4B1D4F, 0x6B2A63, 0x5A2358, 0x7A3374, 0x2EB872, 0xFFB74D, 0xF48FB1, 0xFFF3E0, 0xD7A9C0},
            {0x0B1F14, 0x1B4332, 0x1F4A35, 0x2D6A4F, 0x245A41, 0x33795A, 0xE0A800, 0x95D5B2, 0xF4A261, 0xE9F5EC, 0x9BC1AB},
            {0x2A1B3D, 0x44318D, 0x3A2A66, 0x5440A0, 0x4A3A85, 0x6150A8, 0x00C9A7, 0x7DF9FF, 0xFF9AD5, 0xFFFFFF, 0xC3B8F0}
    };

    static Color BG_TOP, BG_BOTTOM, CARD, CARD_ON, CELL, CELL_HOVER, WIN, X_COLOR, O_COLOR, TEXT, MUTED;

    static { applyColors(0); }

    static void applyColors(int i) {
        int[] t = THEMES[i];
        BG_TOP = new Color(t[0]);
        BG_BOTTOM = new Color(t[1]);
        CARD = new Color(t[2]);
        CARD_ON = new Color(t[3]);
        CELL = new Color(t[4]);
        CELL_HOVER = new Color(t[5]);
        WIN = new Color(t[6]);
        X_COLOR = new Color(t[7]);
        O_COLOR = new Color(t[8]);
        TEXT = new Color(t[9]);
        MUTED = new Color(t[10]);
    }

    static Color lighten(Color c, int d) {
        return new Color(Math.min(255, c.getRed() + d),
                Math.min(255, c.getGreen() + d),
                Math.min(255, c.getBlue() + d));
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    static String titleHtml() {
        return "<html><span style='color:" + hex(X_COLOR) + "'>TIC</span> "
                + "<span style='color:" + hex(TEXT) + "'>TAC</span> "
                + "<span style='color:" + hex(O_COLOR) + "'>TOE</span></html>";
    }

    /** Small window / taskbar icon drawn in code */
    private static Image makeIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x23264A));
        g.fillRoundRect(0, 0, 64, 64, 18, 18);
        g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(X_COLOR);
        g.drawLine(12, 12, 30, 30);
        g.drawLine(30, 12, 12, 30);
        g.setColor(O_COLOR);
        g.drawOval(34, 34, 18, 18);
        g.dispose();
        return img;
    }

    private final Board board = new Board();
    private final ComputerPlayer computer = new ComputerPlayer();
    private final CellButton[][] cells = new CellButton[3][3];

    private final JLabel title = new JLabel("", SwingConstants.CENTER);
    private final JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel modeLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel nameXLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel nameOLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel drawNameLabel = new JLabel("Draws", SwingConstants.CENTER);
    private final JLabel scoreXLabel = new JLabel("0", SwingConstants.CENTER);
    private final JLabel scoreOLabel = new JLabel("0", SwingConstants.CENTER);
    private final JLabel drawLabel = new JLabel("0", SwingConstants.CENTER);
    private final JLabel themeHead = new JLabel("CHOOSE YOUR THEME", SwingConstants.CENTER);
    private RoundPanel cardX, cardO, cardDraw, pill;
    private PillButton nextBtn, restartBtn, homeBtn, soundBtnGame, exitBtn;
    private Overlay overlay;
    private StartScreen startScreen;
    private SetupScreen setupScreen;
    private CardLayout cardLayout;
    private JPanel deck;

    private String nameX = "Player 1", nameO = "Player 2";
    private char current = 'X';
    private char starter = 'O';          // flips at the start of each round, so X goes first
    private boolean vsComputer = false;
    private boolean gameOver = false;
    private int scoreX = 0, scoreO = 0, draws = 0;
    private int totalRounds = 3, roundsPlayed = 0;
    private int themeIndex = 0;
    private javax.swing.Timer computerTimer;

    public TicTacToe() {
        super("Tic-Tac-Toe");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(makeIcon());

        // ================= GAME PAGE =================
        GradientPanel game = new GradientPanel();
        game.setLayout(new BorderLayout(0, sc(12)));
        game.setBorder(new EmptyBorder(sc(16), sc(28), sc(18), sc(28)));

        // ---- Top: title + mode + score cards + status pill ----
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        title.setText(titleHtml());
        title.setFont(font(Font.BOLD, 34));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        modeLabel.setFont(font(Font.PLAIN, 14));
        modeLabel.setForeground(MUTED);
        modeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel scoreRow = new JPanel(new GridLayout(1, 3, sc(12), 0));
        scoreRow.setOpaque(false);
        scoreRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        scoreRow.setPreferredSize(new Dimension(sc(760), sc(92)));
        scoreRow.setMaximumSize(new Dimension(sc(760), sc(92)));
        cardX = makeCard(nameXLabel, scoreXLabel, X_COLOR);
        cardDraw = makeCard(drawNameLabel, drawLabel, MUTED);
        cardO = makeCard(nameOLabel, scoreOLabel, O_COLOR);
        scoreRow.add(cardX);
        scoreRow.add(cardDraw);
        scoreRow.add(cardO);

        pill = new RoundPanel(CARD, sc(44));
        pill.setLayout(new FlowLayout(FlowLayout.CENTER, 0, sc(9)));
        pill.setPreferredSize(new Dimension(sc(760), sc(54)));
        pill.setMaximumSize(new Dimension(sc(760), sc(54)));
        pill.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setFont(font(Font.BOLD, 20));
        pill.add(statusLabel);

        top.add(title);
        top.add(modeLabel);
        top.add(Box.createVerticalStrut(sc(10)));
        top.add(scoreRow);
        top.add(Box.createVerticalStrut(sc(12)));
        top.add(pill);
        game.add(top, BorderLayout.NORTH);

        // ---- Centre: 3x3 board (always square) ----
        JPanel grid = new JPanel(new GridLayout(3, 3, sc(12), sc(12)));
        grid.setOpaque(false);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                CellButton b = new CellButton();
                final int row = r, col = c;
                b.addActionListener(e -> handleClick(row, col));
                cells[r][c] = b;
                grid.add(b);
            }
        }
        game.add(new SquarePanel(grid), BorderLayout.CENTER);

        // ---- Bottom: control buttons + theme buttons ----
        nextBtn = new PillButton("Next Round", X_COLOR);
        restartBtn = new PillButton("Restart Match", WIN);
        homeBtn = new PillButton("Home", MUTED);
        soundBtnGame = new PillButton("Sound: ON", MUTED);
        exitBtn = new PillButton("Exit", O_COLOR);
        nextBtn.addActionListener(e -> newRound());
        restartBtn.addActionListener(e -> { resetScores(); newRound(); });
        homeBtn.addActionListener(e -> goHome());
        soundBtnGame.addActionListener(e -> toggleSound());
        exitBtn.addActionListener(e -> confirmExit());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, sc(12), 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (PillButton b : new PillButton[]{nextBtn, restartBtn, homeBtn, soundBtnGame, exitBtn}) {
            b.setPreferredSize(new Dimension(sc(145), sc(44)));
            btnRow.add(b);
        }

        themeHead.setFont(font(Font.BOLD, 12));
        themeHead.setForeground(MUTED);
        themeHead.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel themeRow = new JPanel(new FlowLayout(FlowLayout.CENTER, sc(10), 0));
        themeRow.setOpaque(false);
        themeRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (int i = 0; i < THEME_NAMES.length; i++) {
            final int idx = i;
            PillButton tb = new PillButton(THEME_NAMES[i], new Color(THEMES[i][7]));
            tb.setFont(font(Font.BOLD, 12));
            tb.setPreferredSize(new Dimension(sc(118), sc(34)));
            tb.addActionListener(e -> setTheme(idx));
            themeRow.add(tb);
        }

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.add(btnRow);
        bottom.add(Box.createVerticalStrut(sc(10)));
        bottom.add(themeHead);
        bottom.add(Box.createVerticalStrut(sc(4)));
        bottom.add(themeRow);
        game.add(bottom, BorderLayout.SOUTH);

        // ================= START + SETUP PAGES =================
        startScreen = new StartScreen(this::openSetup, this::showInstructions,
                this::cycleTheme, this::toggleSound, this::confirmExit);
        setupScreen = new SetupScreen(this::beginMatch, this::backFromSetup);

        cardLayout = new CardLayout();
        deck = new JPanel(cardLayout);
        deck.add(startScreen, "start");
        deck.add(setupScreen, "setup");
        deck.add(game, "game");
        setContentPane(deck);

        // ================= POPUPS =================
        overlay = new Overlay();
        setGlassPane(overlay);
        overlay.setVisible(false);

        setSize(sc(900), sc(760));
        setMinimumSize(new Dimension(sc(780), sc(640)));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);          // opens full screen

        cardLayout.show(deck, "start");
        startScreen.setRunning(true);
    }

    // ---- UI helpers ----
    private RoundPanel makeCard(JLabel nameLabel, JLabel scoreLabel, Color accent) {
        RoundPanel card = new RoundPanel(CARD, sc(26));
        card.setLayout(new GridLayout(2, 1));
        card.setBorder(new EmptyBorder(sc(8), sc(6), sc(8), sc(6)));
        nameLabel.setFont(font(Font.BOLD, 14));
        nameLabel.setForeground(accent);
        scoreLabel.setFont(font(Font.BOLD, 34));
        scoreLabel.setForeground(TEXT);
        card.add(nameLabel);
        card.add(scoreLabel);
        return card;
    }

    // ---- Pages ----
    private void openSetup() {
        SoundFX.start();
        startScreen.setRunning(false);
        setupScreen.prepare();
        cardLayout.show(deck, "setup");
    }

    private void backFromSetup() {
        startScreen.setRunning(true);
        cardLayout.show(deck, "start");
    }

    /** PLAY button on the setup page */
    private void beginMatch() {
        vsComputer = setupScreen.isVsComputer();
        nameX = setupScreen.name1();
        nameO = setupScreen.name2();
        totalRounds = setupScreen.rounds();
        resetScores();
        refreshNames();
        newRound();
        cardLayout.show(deck, "game");
        SoundFX.start();
    }

    private void goHome() {
        if (computerTimer != null) computerTimer.stop();
        overlay.hideOverlay();
        startScreen.setRunning(true);
        cardLayout.show(deck, "start");
    }

    // ---- Sound + theme switches ----
    private void toggleSound() {
        SoundFX.enabled = !SoundFX.enabled;
        String text = "Sound: " + (SoundFX.enabled ? "ON" : "OFF");
        soundBtnGame.setText(text);
        startScreen.setSoundText(SoundFX.enabled);
    }

    private void cycleTheme() {
        setTheme((themeIndex + 1) % THEME_NAMES.length);
    }

    private void setTheme(int index) {
        themeIndex = index;
        applyColors(index);

        title.setText(titleHtml());
        modeLabel.setForeground(MUTED);
        nameXLabel.setForeground(X_COLOR);
        nameOLabel.setForeground(O_COLOR);
        drawNameLabel.setForeground(MUTED);
        scoreXLabel.setForeground(TEXT);
        scoreOLabel.setForeground(TEXT);
        drawLabel.setForeground(TEXT);
        themeHead.setForeground(MUTED);
        cardDraw.setBg(CARD);
        pill.setBg(CARD);
        nextBtn.setBase(X_COLOR);
        restartBtn.setBase(WIN);
        homeBtn.setBase(MUTED);
        soundBtnGame.setBase(MUTED);
        exitBtn.setBase(O_COLOR);
        startScreen.refreshTheme(THEME_NAMES[index]);
        setupScreen.refreshTheme();
        setIconImage(makeIcon());

        if (!gameOver) {
            updateStatus();
        } else {
            statusLabel.setForeground(TEXT);
            cardX.setBg(CARD);
            cardO.setBg(CARD);
        }
        deck.repaint();
    }

    // ---- Popups (all in the game's design) ----
    private void showInstructions() {
        int w = sc(400);
        overlay.showInfo("HOW TO PLAY",
                "<html><div style='width:" + w + "px'>"
                        + "1. Players take turns placing X and O.<br><br>"
                        + "2. Get 3 in a row (across, down or diagonal) to win a round.<br><br>"
                        + "3. If all 9 squares fill with no winner, the round is a draw.<br><br>"
                        + "4. After all the rounds, the player with the most wins is the champion.<br><br>"
                        + "5. Change the look any time with the theme buttons."
                        + "</div></html>",
                X_COLOR, "Got it", null);
    }

    private void confirmExit() {
        overlay.show(false, false, "Exit game?", "Do you really want to quit?", null, null, O_COLOR,
                new String[]{"Yes, Exit", "Stay"},
                new Color[]{O_COLOR, X_COLOR},
                new Runnable[]{() -> System.exit(0), null});
    }

    // ---- Game flow ----
    private void handleClick(int r, int c) {
        if (gameOver) return;
        if (vsComputer && current == 'O') return;          // computer's turn, ignore clicks
        if (!board.place(r, c, current)) {
            SoundFX.taken();
            statusLabel.setText("Square taken!");
            statusLabel.setForeground(MUTED);
            return;
        }
        updateButton(r, c);
        if (checkEnd()) return;

        current = (current == 'X') ? 'O' : 'X';
        updateStatus();
        if (vsComputer && current == 'O') computerTurn();
    }

    private void computerTurn() {
        computerTimer = new javax.swing.Timer(600, e -> {
            int[] move = computer.chooseMove(board);
            board.place(move[0], move[1], 'O');
            updateButton(move[0], move[1]);
            if (!checkEnd()) {
                current = 'X';
                updateStatus();
            }
        });
        computerTimer.setRepeats(false);
        computerTimer.start();
    }

    /** Returns true if the round has ended (win or draw) */
    private boolean checkEnd() {
        int[][] line = board.getWinLine();
        if (line != null) {
            gameOver = true;
            char w = board.get(line[0][0], line[0][1]);
            if (w == 'X') scoreX++; else scoreO++;
            for (int[] p : line) cells[p[0]][p[1]].setHighlight(true);

            String winner = (w == 'X') ? nameX : nameO;
            statusLabel.setText(winner + " wins!");
            statusLabel.setForeground(new Color(0x5C, 0xE0, 0x9A));
            cardX.setBorderColor(w == 'X' ? WIN : null);
            cardO.setBorderColor(w == 'O' ? WIN : null);
            updateScore();
            SoundFX.roundWin();
            endRound(winner + " wins this round!", w == 'X' ? X_COLOR : O_COLOR, 700);
            return true;
        }
        if (board.isFull()) {
            gameOver = true;
            draws++;
            statusLabel.setText("It's a draw!");
            statusLabel.setForeground(MUTED);
            cardX.setBorderColor(null);
            cardO.setBorderColor(null);
            updateScore();
            SoundFX.roundDraw();
            endRound("It's a draw!", MUTED, 500);
            return true;
        }
        return false;
    }

    /** Counts the round; shows a round popup, or the final winner screen after the last round */
    private void endRound(String message, Color accent, int delay) {
        roundsPlayed++;
        updateModeLabel();
        javax.swing.Timer t = new javax.swing.Timer(delay, e -> {
            if (roundsPlayed >= totalRounds) {
                showFinal();
            } else {
                overlay.show(false, false, message, scoreSummary(),
                        "Round " + roundsPlayed + " of " + totalRounds, null, accent,
                        new String[]{"Next Round"}, new Color[]{X_COLOR},
                        new Runnable[]{this::newRound});
            }
        });
        t.setRepeats(false);
        t.start();
    }

    private String scoreSummary() {
        return nameX + ": " + scoreX + "     " + nameO + ": " + scoreO + "     Draws: " + draws;
    }

    // Final winner screen + victory sound
    private void showFinal() {
        String heading, name;
        Color accent;
        boolean tie = false;
        if (scoreX > scoreO) {
            heading = "CHAMPION";
            name = nameX + " wins the match!";
            accent = X_COLOR;
        } else if (scoreO > scoreX) {
            heading = "CHAMPION";
            name = nameO + " wins the match!";
            accent = O_COLOR;
        } else {
            heading = "IT'S A TIE!";
            name = "Nobody wins this time";
            accent = new Color(0xB0BEC5);
            tie = true;
        }
        String score = nameX + "  " + scoreX + "  -  " + scoreO + "  " + nameO;
        String info = totalRounds + " rounds played   |   Draws: " + draws;
        overlay.show(true, true, heading, name, score, info, accent,
                new String[]{"Play Again", "Home", "Exit"},
                new Color[]{X_COLOR, MUTED, O_COLOR},
                new Runnable[]{this::playAgain, this::goHome, this::confirmExit});
        if (tie) SoundFX.finalTie(); else SoundFX.finalWin();
    }

    private void playAgain() {
        resetScores();
        newRound();
    }

    private void newRound() {
        if (computerTimer != null) computerTimer.stop();    // cancel pending computer move
        if (roundsPlayed >= totalRounds) resetScores();     // match finished -> start a new one
        board.reset();
        gameOver = false;
        starter = (starter == 'X') ? 'O' : 'X';             // players take turns starting
        current = starter;
        for (CellButton[] row : cells)
            for (CellButton b : row) {
                b.setMark(' ');
                b.setHighlight(false);
            }
        updateStatus();
        updateScore();
        updateModeLabel();
        if (vsComputer && current == 'O') computerTurn();
    }

    private void resetScores() {
        scoreX = 0;
        scoreO = 0;
        draws = 0;
        roundsPlayed = 0;
        starter = 'O';
    }

    private void updateButton(int r, int c) {
        char m = board.get(r, c);
        SoundFX.place(m);
        cells[r][c].setMark(m);
    }

    private void updateModeLabel() {
        int shown = Math.min(roundsPlayed + 1, totalRounds);
        modeLabel.setText((vsComputer ? "Single Player vs Computer" : "Two Player Mode")
                + "   -   Round " + shown + " of " + totalRounds);
    }

    private void updateStatus() {
        String name = (current == 'X') ? nameX : nameO;
        statusLabel.setText(name + "'s turn (" + current + ")");
        statusLabel.setForeground(current == 'X' ? X_COLOR : O_COLOR);
        cardX.setBg(current == 'X' ? CARD_ON : CARD);       // glow the active player's card
        cardO.setBg(current == 'O' ? CARD_ON : CARD);
        cardX.setBorderColor(current == 'X' ? X_COLOR : null);
        cardO.setBorderColor(current == 'O' ? O_COLOR : null);
    }

    private void refreshNames() {
        nameXLabel.setText(nameX + " (X)");
        nameOLabel.setText(nameO + " (O)");
    }

    private void updateScore() {
        refreshNames();
        scoreXLabel.setText(String.valueOf(scoreX));
        scoreOLabel.setText(String.valueOf(scoreO));
        drawLabel.setText(String.valueOf(draws));
    }

    // ---- Entry point ----
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new TicTacToe().setVisible(true));
    }
}
