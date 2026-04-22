package Client;

import java.util.HashMap;
import common.Message;
import java.util.ArrayList;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;

public class GuiClient extends Application {

    TextField c1;
    Button b1;
    HashMap<String, Scene> sceneMap;
    Client clientConnection;

    ListView<String> listItems2;
    ListView<String> userList;

    Label user1Label = new Label("Player 1: ---");
    Label user2Label = new Label("Player 2: ---");

    String myUsername;
    int[][] logicBoard = new int[8][8]; 
    Button[][] boardButtons = new Button[8][8];
    
    int selectedRow = -1;
    int selectedCol = -1;
    Button selectedButton = null;

    int myPlayerNumber = 0; 
    boolean myTurn = false; // Start false

    Button turnStatusBtn = new Button("Waiting...");
    Button findGameBtn = new Button("Find a Game");
    
    private Stage primaryStage;
    
    Label loginErrorLabel = new Label();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        sceneMap = new HashMap<>();
        listItems2 = new ListView<>();
        userList = new ListView<>();
        userList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        c1 = new TextField();
        b1 = new Button("Send");

        // --- Connection Logic ---
        clientConnection = new Client(data -> {
            Message m = (Message) data;
            Platform.runLater(() -> {
            	if (m.messageType == 1) {
                    if (m.isNameValid) {
                        myUsername = m.username;
                        primaryStage.setTitle("Checkers - " + myUsername);
                        primaryStage.setScene(sceneMap.get("client"));
                        loginErrorLabel.setVisible(false); // Hide any previous errors
                    } else {
                        loginErrorLabel.setText(m.messageText);
                        loginErrorLabel.setVisible(true);
                    }
                }  
                else if (m.messageType == 3) {
                    userList.getItems().clear();
                    userList.getItems().addAll(m.users);
                } 
                else if (m.messageType == 5) { // Game Started
                    user1Label.setText("Player 1 (Red): " + m.username);
                    user2Label.setText("Player 2 (Blue): " + m.target);
                    resetLogicBoard();
                    
                    // CRITICAL: Determine turn based on server message
                    if (myUsername.equals(m.username)) {
                        myPlayerNumber = 1; 
                        myTurn = true;
                    } else {
                        myPlayerNumber = 2; 
                        myTurn = false;
                    }
                    
                    updateBoardVisuals();
                    updateTurnUI();
                    primaryStage.setScene(sceneMap.get("gameBoard"));
                } 
                else if (m.messageType == 6) { // Move Received from Server
                	
                	if (m.username.equals(myUsername)) {
                        // This is my own move echoing back - just update visuals and ignore
                        updateBoardVisuals();
                        return;
                    }
                    
                    // This is opponent's move - update the board
                    int movingPiece = logicBoard[m.fromRow][m.fromCol];
                    logicBoard[m.toRow][m.toCol] = movingPiece;
                    logicBoard[m.fromRow][m.fromCol] = 0;
                    
                    if (Math.abs(m.toRow - m.fromRow) == 2) {
                        logicBoard[(m.toRow + m.fromRow) / 2][(m.toCol + m.fromCol) / 2] = 0;
                    }
                    
                    if (m.toRow == 0 && movingPiece == 1) logicBoard[m.toRow][m.toCol] = 3;
                    if (m.toRow == 7 && movingPiece == 2) logicBoard[m.toRow][m.toCol] = 4;
                    
                    // Opponent made the move, now it's my turn
                    myTurn = true;
                    
                    updateBoardVisuals();
                    updateTurnUI();
                }
                else if (m.messageType == 7) {
                    primaryStage.setScene(sceneMap.get("client"));
                    findGameBtn.setDisable(false);
                    findGameBtn.setText("Find a Game");
                } 
                else if(m.messageType == 8) { // Logout response
                    // Clear all game state
                    myUsername = null;
                    myPlayerNumber = 0;
                    myTurn = false;
                    selectedRow = -1;
                    selectedCol = -1;
                    selectedButton = null;
                    
                    // Clear the user list
                    userList.getItems().clear();
                    
                    // Return to login screen
                    primaryStage.setScene(sceneMap.get("login"));
                    
                    // Reset find game button
                    findGameBtn.setDisable(false);
                    findGameBtn.setText("Find a Game");
                }
                else {
                    listItems2.getItems().add((m.username != null ? m.username : "Server") + ": " + m.messageText);
                }
            });
        });
        clientConnection.start();

        // Setup Scenes
        setupLogin();
        sceneMap.put("client", createLobbyScene());
        sceneMap.put("gameBoard", createGameScene(primaryStage));

        primaryStage.setScene(sceneMap.get("login"));
        primaryStage.show();
    }

    
    private void setupLogin() {
    	VBox loginBox = new VBox(10);
        loginBox.setAlignment(Pos.CENTER);
        
        loginErrorLabel.setTextFill(Color.RED);
        loginErrorLabel.setVisible(false);
        
        TextField t = new TextField();
        t.setPromptText("Username");
        t.setMaxWidth(200);
        
        Button b = new Button("Connect");
        b.setOnAction(e -> {
            String username = t.getText().trim();
            if (username.isEmpty()) {
                loginErrorLabel.setText("Username cannot be empty!");
                loginErrorLabel.setVisible(true);
                return;
            }
            
            loginErrorLabel.setVisible(false);
            Message m = new Message();
            m.username = username;
            m.messageType = 1;
            clientConnection.send(m);
        });
        
        loginBox.getChildren().addAll(new Label("Enter Username:"), t, b, loginErrorLabel);
        sceneMap.put("login", new Scene(loginBox, 300, 200));
    }

    private void resetLogicBoard() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                logicBoard[r][c] = 0;
                if ((r + c) % 2 != 0) {  // Dark squares only
                    if (r < 3) logicBoard[r][c] = 2; // Blue pieces (top)
                    else if (r > 4) logicBoard[r][c] = 1; // Red pieces (bottom)
                }
            }
        }
    }

    public Scene createLobbyScene() {
    	Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> {
            // Just send logout message - don't change scene yet
            // Wait for server acknowledgment
            Message logoutMsg = new Message();
            logoutMsg.messageType = 8;
            logoutMsg.username = myUsername;
            clientConnection.send(logoutMsg);
            
            // Disable logout button to prevent multiple clicks
            logoutBtn.setDisable(true);
            logoutBtn.setText("Logging out...");
        });
        
        findGameBtn.setOnAction(e -> {
            Message m = new Message(); 
            m.messageType = 4; 
            m.username = myUsername;
            clientConnection.send(m);
            findGameBtn.setDisable(true);
            findGameBtn.setText("Searching...");
        });
        
        // Create a top bar with logout button
        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_RIGHT);
        topBar.getChildren().add(logoutBtn);
        
        // Main layout
        VBox layout = new VBox(20, topBar, findGameBtn, new Label("Online Players:"), userList);
        layout.setPadding(new Insets(20));
        
        return new Scene(layout, 400, 500);
    }
    

    public Scene createGameScene(Stage stage) {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(15));

        Button quitBtn = new Button("Quit Game");
        quitBtn.setOnAction(e -> {
            Message q = new Message(); q.messageType = 7;
            clientConnection.send(q);
            stage.setScene(sceneMap.get("client"));
        });
        
        turnStatusBtn.setDisable(true); // Signpost only
        HBox topBar = new HBox(15, quitBtn, turnStatusBtn);
        root.setTop(topBar);

        GridPane boardGrid = new GridPane();
        boardGrid.setAlignment(Pos.CENTER);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Button cell = new Button();
                cell.setMinSize(65, 65);
                boardButtons[r][c] = cell;

                if ((r + c) % 2 != 0) {
                    cell.setStyle("-fx-background-color: #333333;");
                    int row = r; int col = c;
                    cell.setOnAction(e -> handleCellClick(row, col));
                } else {
                    cell.setStyle("-fx-background-color: white;");
                }
                boardGrid.add(cell, c, r);
            }
        }
        
        VBox boardContainer = new VBox(10, user2Label, boardGrid, user1Label);
        boardContainer.setAlignment(Pos.CENTER);
        root.setCenter(boardContainer);

        VBox chatBox = new VBox(10, new Label("Game Chat:"), listItems2, new HBox(5, c1, b1));
        chatBox.setPadding(new Insets(0, 0, 0, 20));
        root.setRight(chatBox);

        return new Scene(root, 1100, 750);
    }
    

    private void handleCellClick(int row, int col) {
        if (!myTurn) return;

        if (selectedButton == null) {
            int piece = logicBoard[row][col];
            // Check if clicking own piece (including kings)
            if ((myPlayerNumber == 1 && (piece == 1 || piece == 3)) || 
                (myPlayerNumber == 2 && (piece == 2 || piece == 4))) {
                selectedRow = row; 
                selectedCol = col;
                selectedButton = boardButtons[row][col];
                selectedButton.setStyle("-fx-background-color: #555500; -fx-border-color: yellow;");
            }
        } else {
            // Attempting to move to an empty dark square
            if (logicBoard[row][col] == 0 && (row + col) % 2 != 0) {
                int dr = row - selectedRow;
                int dc = col - selectedCol;
                int piece = logicBoard[selectedRow][selectedCol];
                boolean isKing = (piece == 3 || piece == 4);
                
                boolean isValidMove = false;
                
                // Check valid move for regular pieces or kings
                if (!isKing) {
                    // Regular pieces can only move forward
                    if (myPlayerNumber == 1) { // Red moves up (decreasing row)
                        if (dr == -1 && Math.abs(dc) == 1) isValidMove = true;
                        else if (dr == -2 && Math.abs(dc) == 2) {
                            // Check if jumping over opponent
                            int midR = (row + selectedRow) / 2;
                            int midC = (col + selectedCol) / 2;
                            int midPiece = logicBoard[midR][midC];
                            if (midPiece == 2 || midPiece == 4) isValidMove = true;
                        }
                    } else { // Blue moves down (increasing row)
                        if (dr == 1 && Math.abs(dc) == 1) isValidMove = true;
                        else if (dr == 2 && Math.abs(dc) == 2) {
                            int midR = (row + selectedRow) / 2;
                            int midC = (col + selectedCol) / 2;
                            int midPiece = logicBoard[midR][midC];
                            if (midPiece == 1 || midPiece == 3) isValidMove = true;
                        }
                    }
                } else {
                    // Kings can move in any direction
                    if (Math.abs(dr) == 1 && Math.abs(dc) == 1) isValidMove = true;
                    else if (Math.abs(dr) == 2 && Math.abs(dc) == 2) {
                        int midR = (row + selectedRow) / 2;
                        int midC = (col + selectedCol) / 2;
                        int midPiece = logicBoard[midR][midC];
                        if (myPlayerNumber == 1 && (midPiece == 2 || midPiece == 4)) isValidMove = true;
                        else if (myPlayerNumber == 2 && (midPiece == 1 || midPiece == 3)) isValidMove = true;
                    }
                }

                if (isValidMove) {
                    // Save the move details before modifying board
                    int fromRow = selectedRow;
                    int fromCol = selectedCol;
                    int toRow = row;
                    int toCol = col;
                    int movingPiece = piece;
                    
                    // Check if this is a jump move
                    boolean isJump = (Math.abs(toRow - fromRow) == 2);
                    
                    // Make the move locally
                    logicBoard[toRow][toCol] = movingPiece;
                    logicBoard[fromRow][fromCol] = 0;
                    
                    if (isJump) {
                        int midR = (toRow + fromRow) / 2;
                        int midC = (toCol + fromCol) / 2;
                        logicBoard[midR][midC] = 0;
                    }
                    
                    // Kinging logic
                    if (myPlayerNumber == 1 && toRow == 0 && (movingPiece == 1 || movingPiece == 3)) {
                        logicBoard[toRow][toCol] = 3;
                    }
                    if (myPlayerNumber == 2 && toRow == 7 && (movingPiece == 2 || movingPiece == 4)) {
                        logicBoard[toRow][toCol] = 4;
                    }

                    // Send to server
                    Message m = new Message();
                    m.messageType = 6; 
                    m.fromRow = fromRow; 
                    m.fromCol = fromCol;
                    m.toRow = toRow; 
                    m.toCol = toCol;
                    m.username = myUsername;
                    clientConnection.send(m);

                    // Update UI and turn
                    myTurn = false;
                    updateBoardVisuals();
                    updateTurnUI();
                }
            }
            
            // Reset selection
            if (selectedButton != null) {
                selectedButton.setStyle("-fx-background-color: #333333;");
            }
            selectedButton = null;
            selectedRow = -1;
            selectedCol = -1;
        }
    }

    private void updateBoardVisuals() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = logicBoard[r][c];
                boardButtons[r][c].setGraphic(null);
                if (piece != 0) {
                    Circle circle = new Circle(22);
                    circle.setFill((piece == 1 || piece == 3) ? Color.RED : Color.BLUE);
                    circle.setStroke(Color.WHITE);
                    if (piece > 2) { // King
                        circle.setStroke(Color.GOLD);
                        circle.setStrokeWidth(4);
                    }
                    boardButtons[r][c].setGraphic(circle);
                }
            }
        }
    }

    private void updateTurnUI() {
        if (myTurn) {
            turnStatusBtn.setText("Your Turn!");
            turnStatusBtn.setStyle("-fx-background-color: #90ee90; -fx-text-fill: black; -fx-font-weight: bold;");
        } else {
            turnStatusBtn.setText("Waiting...");
            turnStatusBtn.setStyle("-fx-background-color: #ffcccb; -fx-text-fill: black;");
        }
    }
    

}