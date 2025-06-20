import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class GomokuServer {
    private ServerSocket serverSocket;
    // 保存客户端连接
    private List<ClientHandler> clients = new ArrayList<>();
    // 记录当前下棋玩家（黑棋先下）
    private boolean isBlackTurn = true;
    // 棋盘状态
    private char[][] board = new char[GomokuConstants.BOARD_SIZE][GomokuConstants.BOARD_SIZE];

    public GomokuServer() {
        try {
            serverSocket = new ServerSocket(GomokuConstants.PORT);
            System.out.println("五子棋服务器启动，端口: " + GomokuConstants.PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("客户端已连接: " + clientSocket);

                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                handler.start();

                // 最多支持 2 个玩家
                if (clients.size() == 2) {
                    // 通知客户端连接成功并分配颜色
                    clients.get(0).send(GomokuConstants.CONNECTED + ":" + GomokuConstants.BLACK);
                    clients.get(1).send(GomokuConstants.CONNECTED + ":" + GomokuConstants.WHITE);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 客户端处理线程
    private class ClientHandler extends Thread {
        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 发送消息给客户端
        public void send(String message) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    String message = (String) in.readObject();
                    if (message.startsWith(GomokuConstants.MOVE)) {
                        // 解析落子坐标：MOVE:x,y
                        String[] parts = message.split(":");
                        int x = Integer.parseInt(parts[1].split(",")[0]);
                        int y = Integer.parseInt(parts[1].split(",")[1]);

                        // 更新棋盘状态
                        GomokuServer.this.board[x][y] = isBlackTurn ? 'B' : 'W';

                        // 转发落子消息给另一个客户端
                        for (ClientHandler c : clients) {
                            if (c != this) {
                                c.send(message);
                            }
                        }

                        // 检查是否获胜
                        if (checkWin(x, y, isBlackTurn ? 'B' : 'W')) {
                            String winMsg = GomokuConstants.WIN + ":" + (isBlackTurn ? "黑棋" : "白棋");
                            for (ClientHandler c : clients) {
                                c.send(winMsg);
                            }
                            // 重置棋盘和玩家状态
                            resetGame();
                            // 关闭连接
                            close();
                            return;
                        }

                        // 切换玩家
                        isBlackTurn = !isBlackTurn;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                close();
            }
        }

        // 检查是否五子连珠
        private boolean checkWin(int x, int y, char player) {
            // 简单实现：仅检查当前落子的横竖斜方向
            return checkLine(x, y, 1, 0, player) || // 水平
                    checkLine(x, y, 0, 1, player) || // 垂直
                    checkLine(x, y, 1, 1, player) || // 正斜线
                    checkLine(x, y, 1, -1, player);  // 反斜线
        }

        private boolean checkLine(int x, int y, int dx, int dy, char player) {
            int count = 1;
            // 向一个方向遍历
            for (int i = 1; i < 5; i++) {
                int nx = x + dx * i;
                int ny = y + dy * i;
                if (nx >= 0 && nx < GomokuConstants.BOARD_SIZE &&
                        ny >= 0 && ny < GomokuConstants.BOARD_SIZE &&
                        GomokuServer.this.board[nx][ny] == player) {
                    count++;
                } else {
                    break;
                }
            }
            // 向相反方向遍历
            for (int i = 1; i < 5; i++) {
                int nx = x - dx * i;
                int ny = y - dy * i;
                if (nx >= 0 && nx < GomokuConstants.BOARD_SIZE &&
                        ny >= 0 && ny < GomokuConstants.BOARD_SIZE &&
                        GomokuServer.this.board[nx][ny] == player) {
                    count++;
                } else {
                    break;
                }
            }
            return count >= 5;
        }

        private void close() {
            try {
                in.close();
                out.close();
                socket.close();
                clients.remove(this);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 重置游戏状态
    private void resetGame() {
        // 重置棋盘
        for (int i = 0; i < GomokuConstants.BOARD_SIZE; i++) {
            for (int j = 0; j < GomokuConstants.BOARD_SIZE; j++) {
                board[i][j] = 0;
            }
        }
        // 重置当前下棋玩家
        isBlackTurn = true;
    }

    public static void main(String[] args) {
        new GomokuServer();
    }
}