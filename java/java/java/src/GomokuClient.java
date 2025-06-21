import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class GomokuClient extends JFrame {
    private char myColor; // 自己的棋子颜色：'B'（黑）或 'W'（白）
    private char[][] board; // 棋盘状态
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private boolean isMyTurn = false; // 是否轮到自己下棋
    private Clip backgroundMusic;
    private Clip moveSound;
    private Clip winSound;
    private Clip loseSound;

    public GomokuClient() {
        board = new char[GomokuConstants.BOARD_SIZE][GomokuConstants.BOARD_SIZE];
        initBoard();

        setTitle("五子棋客户端");
        setSize(600, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 加载音频文件
        loadSounds();

        // 播放背景音乐
        playBackgroundMusic();

        // 绘制棋盘
        JPanel boardPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawBoard(g);
                drawPieces(g);
            }
        };
        boardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (isMyTurn) {
                    int x = e.getX() / 40;
                    int y = e.getY() / 40;
                    if (x >= 0 && x < GomokuConstants.BOARD_SIZE &&
                            y >= 0 && y < GomokuConstants.BOARD_SIZE &&
                            board[x][y] == 0) {
                        // 落子
                        board[x][y] = myColor;
                        isMyTurn = false;
                        // 发送落子消息
                        sendMessage(GomokuConstants.MOVE + ":" + x + "," + y);
                        repaint();
                        // 播放落子音效
                        playMoveSound();
                    }
                }
            }
        });
        add(boardPanel);
    }

    // 加载音频文件
    private void loadSounds() {
        try {
            // 加载背景音乐
            String projectRoot = System.getProperty("user.dir");
            AudioInputStream backgroundStream = AudioSystem.getAudioInputStream(new File(projectRoot+"\\src\\wav\\background_music.wav"));
            backgroundMusic = AudioSystem.getClip();
            backgroundMusic.open(backgroundStream);

            // 加载落子音效
            AudioInputStream moveStream = AudioSystem.getAudioInputStream(new File(projectRoot+"\\src\\wav\\move_sound.wav"));
            moveSound = AudioSystem.getClip();
            moveSound.open(moveStream);

            // 加载胜利音效
            AudioInputStream winStream = AudioSystem.getAudioInputStream(new File(projectRoot+"\\src\\wav\\win_sound.wav"));
            winSound = AudioSystem.getClip();
            winSound.open(winStream);

            // 加载失败音效
            AudioInputStream loseStream = AudioSystem.getAudioInputStream(new File(projectRoot+"\\src\\wav\\lose_sound.wav"));
            loseSound = AudioSystem.getClip();
            loseSound.open(loseStream);
            System.out.println("音乐加载成功");
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            e.printStackTrace();
        }
    }
    // 播放背景音乐
    private void playBackgroundMusic() {
        if (backgroundMusic != null) {
            backgroundMusic.loop(Clip.LOOP_CONTINUOUSLY);
        }
    }

    // 播放落子音效
    private void playMoveSound() {
        if (moveSound != null) {
            moveSound.setFramePosition(0);
            moveSound.start();
        }
    }

    // 播放胜利音效
    private void playWinSound() {
        if (winSound != null) {
            winSound.setFramePosition(0);
            winSound.start();
        }
    }

    // 播放失败音效
    private void playLoseSound() {
        if (loseSound != null) {
            loseSound.setFramePosition(0);
            loseSound.start();
        }
    }

    // 初始化棋盘
    private void initBoard() {
        for (int i = 0; i < GomokuConstants.BOARD_SIZE; i++) {
            for (int j = 0; j < GomokuConstants.BOARD_SIZE; j++) {
                board[i][j] = 0;
            }
        }
    }

    // 绘制棋盘网格
    private void drawBoard(Graphics g) {
        for (int i = 0; i <= GomokuConstants.BOARD_SIZE; i++) {
            g.drawLine(i * 40, 0, i * 40, GomokuConstants.BOARD_SIZE * 40);
            g.drawLine(0, i * 40, GomokuConstants.BOARD_SIZE * 40, i * 40);
        }
    }

    // 绘制棋子
    private void drawPieces(Graphics g) {
        for (int i = 0; i < GomokuConstants.BOARD_SIZE; i++) {
            for (int j = 0; j < GomokuConstants.BOARD_SIZE; j++) {
                if (board[i][j] == 'B') {
                    g.setColor(Color.BLACK);
                    g.fillOval(i * 40 + 5, j * 40 + 5, 30, 30);
                } else if (board[i][j] == 'W') {
                    g.setColor(Color.WHITE);
                    g.fillOval(i * 40 + 5, j * 40 + 5, 30, 30);
                    g.setColor(Color.BLACK);
                    g.drawOval(i * 40 + 5, j * 40 + 5, 30, 30);
                }
            }
        }
    }

    // 连接服务端
    public void connect(String serverIp) {
        try {
            socket = new Socket(serverIp, GomokuConstants.PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            System.out.println("已连接到服务器");

            // 等待服务端分配颜色
            String response = (String) in.readObject();
            if (response.startsWith(GomokuConstants.CONNECTED)) {
                String color = response.split(":")[1];
                myColor = color.equals(GomokuConstants.BLACK) ? 'B' : 'W';
                isMyTurn = (myColor == 'B'); // 黑棋先下
                JOptionPane.showMessageDialog(this, "您是"
                        + (myColor == 'B' ? "黑棋" : "白棋")
                        + "，" + (isMyTurn ? "请先落子" : "等待对手落子"));
            }

            // 启动接收线程
            new Thread(() -> {
                try {
                    while (true) {
                        String message = (String) in.readObject();
                        if (message.startsWith(GomokuConstants.MOVE)) {
                            // 解析对手落子：MOVE:x,y
                            String[] parts = message.split(":");
                            int x = Integer.parseInt(parts[1].split(",")[0]);
                            int y = Integer.parseInt(parts[1].split(",")[1]);
                            board[x][y] = (myColor == 'B') ? 'W' : 'B';
                            isMyTurn = true;
                            repaint();
                            // 播放落子音效
                            playMoveSound();
                        } else if (message.startsWith(GomokuConstants.WIN)) {
                            // 提示获胜
                            String winner = message.split(":")[1];
                            if (winner.contains((myColor == 'B' ? "黑棋" : "白棋"))) {
                                playWinSound();
                            } else {
                                playLoseSound();
                            }
                            JOptionPane.showMessageDialog(this, winner + "获胜！");
                            close();
                            return;
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                    close();
                }
            }).start();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "连接失败，请检查服务器地址");
        }
    }

    // 发送消息
    private void sendMessage(String message) {
        try {
            out.writeObject(message);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 关闭连接
    private void close() {
        try {
            in.close();
            out.close();
            socket.close();
            // 停止背景音乐
            if (backgroundMusic != null) {
                backgroundMusic.stop();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GomokuClient client = new GomokuClient();
            client.setVisible(true);
            client.connect("10.174.143.48");
        });
    }
}