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
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.animation.RotateTransition;
import javafx.util.Duration;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.Color;
import javafx.scene.layout.Pane;

public class GuiClient extends Application {
	
	//initalizing all the labels
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
    
    private int redCaptures = 0;   // Player 1 (Red/Bottom) captures
    private int blueCaptures = 0;  // Player 2 (Blue/Top) captures
    private Label redCapturesLabel = new Label("RED: 0");
    private Label blueCapturesLabel = new Label("BLUE: 0");
    
    private Scene gameOverScene;
    private String currentWinner = "";
    private String currentLoser = "";
    private Label gameOverWinnerLabel;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
    	this.primaryStage = primaryStage; 
        sceneMap = new HashMap<>();
        listItems2 = new ListView<>();
        userList = new ListView<>();
        userList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        c1 = new TextField();
        b1 = new Button("Send");
        
        b1.setOnAction(e -> sendChatMessage());
        c1.setOnAction(e -> sendChatMessage());

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
                        int midR = (m.toRow + m.fromRow) / 2;
                        int midC = (m.toCol + m.fromCol) / 2;
                        int capturedPiece = logicBoard[midR][midC];
                        logicBoard[midR][midC] = 0;
                        addCapture(capturedPiece);  // ADD THIS LINE
                    }
                    
                    if (m.toRow == 0 && movingPiece == 1) logicBoard[m.toRow][m.toCol] = 3;
                    if (m.toRow == 7 && movingPiece == 2) logicBoard[m.toRow][m.toCol] = 4;
                    
                    myTurn = true;
                    updateBoardVisuals();
                    updateTurnUI();
                    
                    checkForWinner();
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
                else if (m.messageType == 9) { // Rematch acknowledged
                    // Reset game for both players
                    resetLogicBoard();
                    resetCaptures();
                    
                    if (myUsername.equals(m.username)) {
                        // I requested rematch, opponent agreed
                        myTurn = true;
                    } else {
                        // Opponent requested rematch
                        myTurn = false;
                    }
                    
                    updateBoardVisuals();
                    updateTurnUI();
                    primaryStage.setScene(sceneMap.get("gameBoard"));
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
        // Outer container — dark background
        StackPane outerPane = new StackPane();
        outerPane.setStyle("-fx-background-color: #1a1a2e;");

        // Card
        VBox card = new VBox(18);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(340);
        card.setPadding(new Insets(40, 36, 40, 36));
        card.setStyle(
            "-fx-background-color: #16213e;" +
            "-fx-background-radius: 16px;" +
            "-fx-border-color: #0f3460;" +
            "-fx-border-width: 1.5px;" +
            "-fx-border-radius: 16px;"
        );

        // Crown / checkers icon label
        Label iconLabel = new Label("♟");
        iconLabel.setStyle("-fx-font-size: 48px;");

        Label titleLabel = new Label("CHECKERS");
        titleLabel.setStyle(
            "-fx-text-fill: #f1c40f;" +
            "-fx-font-size: 28px;" +
            "-fx-font-weight: bold;" +
            "-fx-letter-spacing: 4px;"
        );

        Label subtitleLabel = new Label("Enter a username to play");
        subtitleLabel.setStyle(
            "-fx-text-fill: #a0aec0;" +
            "-fx-font-size: 13px;"
        );

        // Divider
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #0f3460;");

        TextField t = new TextField();
        t.setPromptText("Username...");
        t.setMaxWidth(Double.MAX_VALUE);
        t.setStyle(
            "-fx-background-color: #0f3460;" +
            "-fx-text-fill: white;" +
            "-fx-prompt-text-fill: #718096;" +
            "-fx-border-color: #2d3748;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 10px 14px;" +
            "-fx-font-size: 14px;"
        );

        loginErrorLabel.setTextFill(Color.web("#fc8181"));
        loginErrorLabel.setStyle("-fx-font-size: 12px;");
        loginErrorLabel.setVisible(false);

        Button b = new Button("Connect to Server");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setStyle(
            "-fx-background-color: #f1c40f;" +
            "-fx-text-fill: #1a1a2e;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 14px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 11px 0;" +
            "-fx-cursor: hand;"
        );

        b.setOnMouseEntered(e -> b.setStyle(
            "-fx-background-color: #d4ac0d;" +
            "-fx-text-fill: #1a1a2e;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 14px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 11px 0;" +
            "-fx-cursor: hand;"
        ));
        b.setOnMouseExited(e -> b.setStyle(
            "-fx-background-color: #f1c40f;" +
            "-fx-text-fill: #1a1a2e;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 14px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 11px 0;" +
            "-fx-cursor: hand;"
        ));

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

        t.setOnAction(e -> b.fire());

        card.getChildren().addAll(iconLabel, titleLabel, subtitleLabel, sep, t, loginErrorLabel, b);
        outerPane.getChildren().add(card);

        sceneMap.put("login", new Scene(outerPane, 500, 420));
    }

    private void resetLogicBoard() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                logicBoard[r][c] = 0;
                if ((r + c) % 2 != 0) {
                    if (r < 3) logicBoard[r][c] = 2; // Blue pieces (top)
                    else if (r > 4) logicBoard[r][c] = 1; // Red pieces (bottom)
                }
            }
        }
        resetCaptures(); // Reset capture counters
    }

    public Scene createLobbyScene() {
        // Outer container — dark background matching login
        StackPane outerPane = new StackPane();
        outerPane.setStyle("-fx-background-color: #1a1a2e;");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: transparent;");
        root.setPadding(new Insets(24));

        // --- TOP BAR ---
        Label titleLabel = new Label("♟  CHECKERS");
        titleLabel.setStyle(
            "-fx-text-fill: #f1c40f;" +
            "-fx-font-size: 22px;" +
            "-fx-font-weight: bold;"
        );

        Label usernameDisplay = new Label("Logged in as: " + (myUsername != null ? myUsername : ""));
        usernameDisplay.setStyle(
            "-fx-text-fill: #a0aec0;" +
            "-fx-font-size: 13px;"
        );

        Button logoutBtn = new Button("Logout");
        logoutBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #fc8181;" +
            "-fx-border-color: #fc8181;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 7px 16px;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;"
        );
        logoutBtn.setOnMouseEntered(e -> logoutBtn.setStyle(
            "-fx-background-color: #fc8181;" +
            "-fx-text-fill: #1a1a2e;" +
            "-fx-border-color: #fc8181;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 7px 16px;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;"
        ));
        logoutBtn.setOnMouseExited(e -> logoutBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #fc8181;" +
            "-fx-border-color: #fc8181;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 7px 16px;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;"
        ));
        logoutBtn.setOnAction(e -> {
            Message logoutMsg = new Message();
            logoutMsg.messageType = 8;
            logoutMsg.username = myUsername;
            clientConnection.send(logoutMsg);
            logoutBtn.setDisable(true);
            logoutBtn.setText("Logging out...");
        });

        VBox titleBox = new VBox(4, titleLabel, usernameDisplay);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        topBar.getChildren().addAll(titleBox, logoutBtn);
        topBar.setPadding(new Insets(0, 0, 20, 0));
        root.setTop(topBar);

        // --- CENTER CARD ---
        VBox centerCard = new VBox(20);
        centerCard.setAlignment(Pos.TOP_CENTER);
        centerCard.setPadding(new Insets(28, 28, 28, 28));
        centerCard.setMaxWidth(420);
        centerCard.setStyle(
            "-fx-background-color: #16213e;" +
            "-fx-background-radius: 16px;" +
            "-fx-border-color: #0f3460;" +
            "-fx-border-width: 1.5px;" +
            "-fx-border-radius: 16px;"
        );

        // Find Game button
        findGameBtn.setText("Find a Game");
        findGameBtn.setMaxWidth(Double.MAX_VALUE);
        findGameBtn.setStyle(
            "-fx-background-color: #f1c40f;" +
            "-fx-text-fill: #1a1a2e;" +
            "-fx-font-weight: bold;" +
            "-fx-font-size: 15px;" +
            "-fx-background-radius: 10px;" +
            "-fx-padding: 13px 0;" +
            "-fx-cursor: hand;"
        );
        findGameBtn.setOnMouseEntered(e -> {
            if (!findGameBtn.isDisabled()) findGameBtn.setStyle(
                "-fx-background-color: #d4ac0d;" +
                "-fx-text-fill: #1a1a2e;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 15px;" +
                "-fx-background-radius: 10px;" +
                "-fx-padding: 13px 0;" +
                "-fx-cursor: hand;"
            );
        });
        findGameBtn.setOnMouseExited(e -> {
            if (!findGameBtn.isDisabled()) findGameBtn.setStyle(
                "-fx-background-color: #f1c40f;" +
                "-fx-text-fill: #1a1a2e;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 15px;" +
                "-fx-background-radius: 10px;" +
                "-fx-padding: 13px 0;" +
                "-fx-cursor: hand;"
            );
        });
        findGameBtn.setOnAction(e -> {
            Message m = new Message();
            m.messageType = 4;
            m.username = myUsername;
            clientConnection.send(m);
            findGameBtn.setDisable(true);
            findGameBtn.setText("Searching for opponent...");
            findGameBtn.setStyle(
                "-fx-background-color: #2d3748;" +
                "-fx-text-fill: #a0aec0;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 15px;" +
                "-fx-background-radius: 10px;" +
                "-fx-padding: 13px 0;"
            );
        });

        // Online players section
        Label playersHeader = new Label("ONLINE PLAYERS");
        playersHeader.setStyle(
            "-fx-text-fill: #a0aec0;" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-letter-spacing: 2px;"
        );

        userList.setStyle(
            "-fx-background-color: #0f3460;" +
            "-fx-background-radius: 10px;" +
            "-fx-border-color: #2d3748;" +
            "-fx-border-radius: 10px;" +
            "-fx-control-inner-background: #0f3460;" +
            "-fx-font-size: 13px;" +
            "-fx-text-fill: white;"
        );
        userList.setPrefHeight(220);

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #0f3460;");

        centerCard.getChildren().addAll(findGameBtn, sep, playersHeader, userList);

        StackPane cardWrapper = new StackPane(centerCard);
        cardWrapper.setAlignment(Pos.TOP_CENTER);
        root.setCenter(cardWrapper);

        outerPane.getChildren().add(root);
        return new Scene(outerPane, 500, 520);
    }
    

    public Scene createGameScene(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1a1a2e;");
        root.setPadding(new Insets(10));

        // --- TOP BAR ---
        Button quitBtn = new Button("Quit Game");
        quitBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #fc8181;" +
            "-fx-border-color: #fc8181;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 7px 16px;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;"
        );
        quitBtn.setOnMouseEntered(e -> quitBtn.setStyle(
            "-fx-background-color: #fc8181;" +
            "-fx-text-fill: #1a1a2e;" +
            "-fx-border-color: #fc8181;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 7px 16px;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;"
        ));
        quitBtn.setOnMouseExited(e -> quitBtn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #fc8181;" +
            "-fx-border-color: #fc8181;" +
            "-fx-border-radius: 8px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 7px 16px;" +
            "-fx-font-size: 13px;" +
            "-fx-cursor: hand;"
        ));
        quitBtn.setOnAction(e -> {
            Message q = new Message();
            q.messageType = 7;
            clientConnection.send(q);
            stage.setScene(sceneMap.get("client"));
            resetCaptures();
        });

        Label gameTitle = new Label("♟  CHECKERS");
        gameTitle.setStyle(
            "-fx-text-fill: #f1c40f;" +
            "-fx-font-size: 20px;" +
            "-fx-font-weight: bold;"
        );

        turnStatusBtn.setDisable(true);
        turnStatusBtn.setStyle(
            "-fx-background-color: #2d3748;" +
            "-fx-text-fill: #a0aec0;" +
            "-fx-font-size: 13px;" +
            "-fx-background-radius: 8px;" +
            "-fx-padding: 7px 16px;" +
            "-fx-border-radius: 8px;"
        );

        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 10, 0));
        Region spacer1 = new Region();
        Region spacer2 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        topBar.getChildren().addAll(quitBtn, spacer1, gameTitle, spacer2, turnStatusBtn);
        root.setTop(topBar);

        // --- BOARD ---
        // Player name labels
        user2Label.setStyle(
            "-fx-text-fill: #63b3ed;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;"
        );
        user1Label.setStyle(
            "-fx-text-fill: #fc8181;" +
            "-fx-font-size: 14px;" +
            "-fx-font-weight: bold;"
        );

        GridPane boardGrid = new GridPane();
        boardGrid.setAlignment(Pos.CENTER);
        boardGrid.setStyle(
            "-fx-border-color: #f1c40f;" +
            "-fx-border-width: 3px;" +
            "-fx-border-radius: 4px;"
        );

        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                Button cell = new Button();
                cell.setMinSize(50, 50);
                cell.setMaxSize(50, 50);
                boardButtons[r][c] = cell;

                if ((r + c) % 2 != 0) {
                    cell.setStyle("-fx-background-color: #2d3748; -fx-background-radius: 0;");
                    int row = r; int col = c;
                    cell.setOnAction(e -> handleCellClick(row, col));
                } else {
                    cell.setStyle("-fx-background-color: #edf2f7; -fx-background-radius: 0;");
                }
                boardGrid.add(cell, c, r);
            }
        }

        // Row/col labels around the board
        GridPane labeledBoard = new GridPane();
        labeledBoard.setAlignment(Pos.CENTER);
        labeledBoard.setHgap(0);
        labeledBoard.setVgap(0);

        String[] colLetters = {"A", "B", "C", "D", "E", "F", "G", "H"};

     // Top & Bottom Column Labels (A-H)
     for (int c = 0; c < 8; c++) {
         // Top Row
         Label lblTop = new Label(colLetters[c]);
         lblTop.setMinWidth(50);
         lblTop.setAlignment(Pos.CENTER);
         lblTop.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-font-weight: bold;");
         labeledBoard.add(lblTop, c + 1, 0);

         // Bottom Row
         Label lblBot = new Label(colLetters[c]);
         lblBot.setMinWidth(50);
         lblBot.setAlignment(Pos.CENTER);
         lblBot.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-font-weight: bold;");
         labeledBoard.add(lblBot, c + 1, 9);
     }

     // Left & Right Row Labels (8-1)
     for (int r = 0; r < 8; r++) {
         // Left Side Labels
         Label lblLeft = new Label(String.valueOf(8 - r));
         lblLeft.setMinHeight(50); // Set height to match the cell height
         lblLeft.setMinWidth(20);
         lblLeft.setAlignment(Pos.CENTER_RIGHT);
         lblLeft.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-padding: 0 8px 0 0;");
         labeledBoard.add(lblLeft, 0, r + 1);

         // Right Side Labels (Optional, but helps balance the UI)
         Label lblRight = new Label(String.valueOf(8 - r));
         lblRight.setMinHeight(50);
         lblRight.setMinWidth(20);
         lblRight.setAlignment(Pos.CENTER_LEFT);
         lblRight.setStyle("-fx-text-fill: #718096; -fx-font-size: 11px; -fx-padding: 0 0 0 8px;");
         labeledBoard.add(lblRight, 9, r + 1);
     }

     // Add the board in the middle (taking up columns 1-8 and rows 1-8)
     labeledBoard.add(boardGrid, 1, 1, 8, 8);

        VBox boardContainer = new VBox(5, user2Label, labeledBoard, user1Label);
        boardContainer.setAlignment(Pos.CENTER);
        root.setCenter(boardContainer);

     /// --- 3. BOTTOM CHAT (The Vertical Stack) ---
        HBox chatFooter = new HBox(20); 
        chatFooter.setAlignment(Pos.CENTER);
        chatFooter.setPadding(new Insets(15));
        chatFooter.setStyle(
            "-fx-background-color: #16213e; " +
            "-fx-background-radius: 15; " +
            "-fx-border-color: #0f3460; " +
            "-fx-border-radius: 15;"
        );

        // Left Side: Label and the Chat History list
        Label chatHeader = new Label("GAME CHAT");
        chatHeader.setStyle("-fx-text-fill: #a0aec0; -fx-font-size: 11px; -fx-font-weight: bold; -fx-letter-spacing: 1px;");
        
        listItems2.setPrefWidth(300);  // Wider for readability
        listItems2.setPrefHeight(120); // Shorter for bottom placement
        listItems2.setStyle(
            "-fx-background-color: #0f3460; " +
            "-fx-background-radius: 10px; " +
            "-fx-border-color: #2d3748; " +
            "-fx-border-radius: 10px; " +
            "-fx-control-inner-background: #0f3460; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 12px;"
        );
        VBox chatHistoryBox = new VBox(5, chatHeader, listItems2);

        // Right Side: Grouping Input and Captures
        c1.setPromptText("Type...");
        c1.setPrefWidth(140);
        c1.setStyle("-fx-background-color: #0f3460; -fx-text-fill: white; -fx-prompt-text-fill: #718096; -fx-border-color: #2d3748; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 8px;");

        b1.setText("Send");
        b1.setStyle("-fx-background-color: #f1c40f; -fx-text-fill: #1a1a2e; -fx-font-weight: bold; -fx-background-radius: 8px; -fx-padding: 8px 14px; -fx-cursor: hand;");

        HBox chatInputRow = new HBox(5, c1, b1);
        chatInputRow.setAlignment(Pos.CENTER);

     // 1. Re-initialize the capturePanel HBox
        HBox capturePanel = new HBox(12); // Spacing between items
        capturePanel.setAlignment(Pos.CENTER);
        capturePanel.setPadding(new Insets(8));
        capturePanel.setStyle(
            "-fx-background-color: #0f3460; " +
            "-fx-background-radius: 10px; " +
            "-fx-border-color: #2d3748; " +
            "-fx-border-width: 1px;"
        );

        // 2. Re-style the labels to make sure they are visible against the dark blue
        Label capturesTitle = new Label("Captures:");
        capturesTitle.setStyle("-fx-text-fill: #a0aec0; -fx-font-weight: bold; -fx-font-size: 11px;");
        
        // Ensure the capture numbers are styled and have their current text
        redCapturesLabel.setText("RED: " + redCaptures);
        redCapturesLabel.setStyle("-fx-text-fill: #fc8181; -fx-font-weight: bold; -fx-font-size: 13px;");
        
        blueCapturesLabel.setText("BLUE: " + blueCaptures);
        blueCapturesLabel.setStyle("-fx-text-fill: #63b3ed; -fx-font-weight: bold; -fx-font-size: 13px;");

        // 3. Add them to the panel (This "claims" them for this scene)
        capturePanel.getChildren().clear(); // Clear any old parents
        capturePanel.getChildren().addAll(capturesTitle, redCapturesLabel, blueCapturesLabel);

        // 4. Put it all together in the controls box
        VBox chatControls = new VBox(10, chatInputRow, capturePanel);
        chatControls.setAlignment(Pos.CENTER);

        // Combine Left and Right into the Footer
        chatFooter.getChildren().addAll(chatHistoryBox, chatControls);

        // Attach to the bottom of the main layout
        root.setBottom(chatFooter);
        BorderPane.setMargin(chatFooter, new Insets(10, 0, 0, 0));

        // Button actions
        b1.setOnAction(e -> sendChatMessage());
        c1.setOnAction(e -> sendChatMessage());

        // Final Scene Sizing for half-screen
        return new Scene(root, 620, 820);
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
                        int capturedPiece = logicBoard[midR][midC];
                        logicBoard[midR][midC] = 0;
                        addCapture(capturedPiece);
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
                    
                    // Check for winner after move
                    if (checkForWinner()) {
                        return; // Stop if game is over
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
    }

    private void updateBoardVisuals() {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = logicBoard[r][c];
                boardButtons[r][c].setGraphic(null);
                if (piece != 0) {
                    Circle circle = new Circle(18);
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
            turnStatusBtn.setStyle(
                "-fx-background-color: #276749;" +
                "-fx-text-fill: #9ae6b4;" +
                "-fx-font-weight: bold;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 8px;" +
                "-fx-padding: 7px 16px;"
            );
        } else {
            turnStatusBtn.setText("Waiting...");
            turnStatusBtn.setStyle(
                "-fx-background-color: #2d3748;" +
                "-fx-text-fill: #a0aec0;" +
                "-fx-font-size: 13px;" +
                "-fx-background-radius: 8px;" +
                "-fx-padding: 7px 16px;"
            );
        }
    }
    
    private void sendChatMessage() {
        String message = c1.getText().trim();
        if (message.isEmpty()) {
            return;
        }
        
        Message chatMessage = new Message();
        chatMessage.messageType = 0;  // Chat message
        chatMessage.messageText = message;
        chatMessage.username = myUsername;
        chatMessage.target = "All";
        
        clientConnection.send(chatMessage);
        c1.clear();
    }
    
    private void updateCaptureLabels() {
        redCapturesLabel.setText("RED: " + redCaptures);
        blueCapturesLabel.setText("BLUE: " + blueCaptures);
        
        // Optional: Add visual feedback when someone is winning
        if (redCaptures >= 10) {
            redCapturesLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold; -fx-font-size: 14px;");
        } else if (blueCaptures >= 10) {
            blueCapturesLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold; -fx-font-size: 14px;");
        } else {
            redCapturesLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 14px;");
            blueCapturesLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold; -fx-font-size: 14px;");
        }
    }

    private void addCapture(int capturedPieceType) {
    	if (capturedPieceType == 1 || capturedPieceType == 3) {
            blueCaptures++;
        } else if (capturedPieceType == 2 || capturedPieceType == 4) {
            redCaptures++;
        }
        updateCaptureLabels();
        
        // Check if this capture caused a win
        checkForWinner();
    }

    private void resetCaptures() {
        redCaptures = 0;
        blueCaptures = 0;
        updateCaptureLabels();
    }
    
    private boolean checkForWinner() {
        int redPieces = 0;
        int bluePieces = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (logicBoard[r][c] == 1 || logicBoard[r][c] == 3) redPieces++;
                if (logicBoard[r][c] == 2 || logicBoard[r][c] == 4) bluePieces++;
            }
        }

        // Condition 1: No pieces left
        if (redPieces == 0) { showWinner("BLUE (Player 2)"); return true; }
        if (bluePieces == 0) { showWinner("RED (Player 1)"); return true; }

        // Condition 2: No legal moves for the person who is supposed to go next
        // If it's my turn, and I can't move, I lose.
        if (myTurn && !hasLegalMoves(myPlayerNumber)) {
            showWinner("Opponent");
            return true;
        }
        
        // If it's the opponent's turn, and they can't move, I win.
        int opponentNum = (myPlayerNumber == 1) ? 2 : 1;
        if (!myTurn && !hasLegalMoves(opponentNum)) {
            showWinner("You");
            return true;
        }

        return false;
    }
    
    
    private boolean hasLegalMoves(int playerNum) {
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = logicBoard[r][c];
                
                // Check if this piece belongs to the requested player
                boolean isPlayersPiece = (playerNum == 1 && (piece == 1 || piece == 3)) ||
                                         (playerNum == 2 && (piece == 2 || piece == 4));

                if (isPlayersPiece) {
                    int[] dr = {-1, -1, 1, 1};
                    int[] dc = {-1, 1, -1, 1};
                    
                    for (int i = 0; i < 4; i++) {
                        // 1. Check for regular moves
                        int nr = r + dr[i];
                        int nc = c + dc[i];
                        
                        // Regular pieces can only move in their specific directions
                        // (Unless you want to simplify and just use your existing logic here)
                        // For a robust check, reuse your logic from handleCellClick:
                        if (isValidMoveCheck(r, c, nr, nc, piece, playerNum)) return true;

                        // 2. Check for jump moves
                        int jr = r + (dr[i] * 2);
                        int jc = c + (dc[i] * 2);
                        if (isValidMoveCheck(r, c, jr, jc, piece, playerNum)) return true;
                    }
                }
            }
        }
        return false;
    }

    // Helper to validate a theoretical move for the check
    private boolean isValidMoveCheck(int fR, int fC, int tR, int tC, int piece, int pNum) {
        if (tR < 0 || tR >= 8 || tC < 0 || tC >= 8) return false;
        if (logicBoard[tR][tC] != 0 || (tR + tC) % 2 == 0) return false;

        int dr = tR - fR;
        int dc = Math.abs(tC - fC);
        boolean isKing = (piece == 3 || piece == 4);

        if (!isKing) {
            if (pNum == 1 && dr >= 0) return false; // Red must move up
            if (pNum == 2 && dr <= 0) return false; // Blue must move down
        }

        if (Math.abs(dr) == 1 && dc == 1) return true; // Simple move

        if (Math.abs(dr) == 2 && dc == 2) { // Jump move
            int mR = (fR + tR) / 2;
            int mC = (fC + tC) / 2;
            int mP = logicBoard[mR][mC];
            if (pNum == 1 && (mP == 2 || mP == 4)) return true;
            if (pNum == 2 && (mP == 1 || mP == 3)) return true;
        }
        return false;
    }
    

    private void showWinner(String winner) {
        Platform.runLater(() -> {
            currentWinner = winner;
            
            // Always recreate so the label is never null
            gameOverScene = createGameOverScene();
            sceneMap.put("gameOver", gameOverScene);
            
            // Now update the label (it's freshly set by createGameOverScene)
            if (gameOverWinnerLabel != null) {
                if (myPlayerNumber == 1 && (winner.contains("RED") || winner.equals("You"))) {
                    gameOverWinnerLabel.setText(myUsername + " (RED) WINS!");
                } else if (myPlayerNumber == 2 && (winner.contains("BLUE") || winner.equals("You"))) {
                    gameOverWinnerLabel.setText(myUsername + " (BLUE) WINS!");
                } else if (winner.equals("Opponent")) {
                    gameOverWinnerLabel.setText("Opponent WINS!");
                } else {
                    gameOverWinnerLabel.setText(winner + " WINS!");
                }
            }
            
            primaryStage.setScene(gameOverScene);
        });
    }
    
    private Scene createGameOverScene() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: linear-gradient(from 0% 0% to 100% 100%, #1a1a2e, #16213e);");
        
        // Center content
        VBox centerBox = new VBox(30);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.setPadding(new Insets(50));
        
        // Winner announcement
        Label winnerLabel = new Label();
        winnerLabel.setStyle("-fx-text-fill: #f1c40f; -fx-font-size: 48px; -fx-font-weight: bold;");
        
        Label winnerNameLabel = new Label();
        winnerNameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 32px; -fx-font-weight: bold;");
        
        Label congratulationsLabel = new Label("🎉 CONGRATULATIONS! 🎉");
        congratulationsLabel.setStyle("-fx-text-fill: #f1c40f; -fx-font-size: 36px; -fx-font-weight: bold;");
        
        // Buttons
        HBox buttonBox = new HBox(30);
        buttonBox.setAlignment(Pos.CENTER);
        
        Button rematchBtn = new Button("🔄 REMATCH");
        rematchBtn.setStyle(
            "-fx-background-color: #2ecc71; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 18px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 12px 24px; " +
            "-fx-background-radius: 8px;"
        );
        rematchBtn.setOnAction(e -> requestRematch());
        
        Button lobbyBtn = new Button("🏠 RETURN TO LOBBY");
        lobbyBtn.setStyle(
            "-fx-background-color: #e74c3c; " +
            "-fx-text-fill: white; " +
            "-fx-font-size: 18px; " +
            "-fx-font-weight: bold; " +
            "-fx-padding: 12px 24px; " +
            "-fx-background-radius: 8px;"
        );
        lobbyBtn.setOnAction(e -> returnToLobby());
        
        buttonBox.getChildren().addAll(rematchBtn, lobbyBtn);
        
        centerBox.getChildren().addAll(
            congratulationsLabel,
            winnerLabel,
            winnerNameLabel,
            buttonBox
        );
        
        root.setCenter(centerBox);
        
        // Add confetti animation (simple version)
        Pane confettiPane = new Pane();
        confettiPane.setMouseTransparent(true);
        root.getChildren().add(confettiPane);
        
        // Start confetti animation
        startConfetti(confettiPane);
        
        // Store labels for updating
        gameOverWinnerLabel = winnerNameLabel;
        
        return new Scene(root, 1200, 800);
    }

    private void startConfetti(Pane confettiPane) {
        Timeline confettiTimeline = new Timeline();
        confettiTimeline.setCycleCount(Timeline.INDEFINITE);
        
        // Create falling confetti
        for (int i = 0; i < 100; i++) {
            javafx.scene.shape.Rectangle confetti = new javafx.scene.shape.Rectangle(8, 8);
            confetti.setFill(getRandomConfettiColor());
            confetti.setX(Math.random() * 1200);
            confetti.setY(Math.random() * 800 - 800);
            confettiPane.getChildren().add(confetti);
            
            // Animate each confetti piece
            javafx.animation.TranslateTransition transition = new javafx.animation.TranslateTransition(
                javafx.util.Duration.seconds(3 + Math.random() * 2), 
                confetti
            );
            transition.setByY(900);
            transition.setCycleCount(Timeline.INDEFINITE);
            transition.setAutoReverse(false);
            transition.play();
            
            // Add rotation for better effect
            javafx.animation.RotateTransition rotate = new javafx.animation.RotateTransition(
                javafx.util.Duration.seconds(1 + Math.random()), 
                confetti
            );
            rotate.setByAngle(360);
            rotate.setCycleCount(Timeline.INDEFINITE);
            rotate.play();
        }
    }

    private Color getRandomConfettiColor() {
        Color[] colors = {Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE, Color.PURPLE, Color.ORANGE, Color.PINK};
        return colors[(int)(Math.random() * colors.length)];
    }
    
    private void requestRematch() {
        // Send rematch request to server
        Message rematchMsg = new Message();
        rematchMsg.messageType = 9;  // New message type for rematch
        rematchMsg.username = myUsername;
        clientConnection.send(rematchMsg);
        
        // Reset game state
        resetLogicBoard();
        resetCaptures();
        myTurn = true;
        myPlayerNumber = 1;  // Assuming red starts
        updateBoardVisuals();
        updateTurnUI();
        
        // Go back to game board
        primaryStage.setScene(sceneMap.get("gameBoard"));
    }

    private void returnToLobby() {
        Message quitMsg = new Message();
        quitMsg.messageType = 7;  // Quit game
        clientConnection.send(quitMsg);
        
        // Reset all game state
        myPlayerNumber = 0;
        myTurn = false;
        selectedRow = -1;
        selectedCol = -1;
        selectedButton = null;
        resetCaptures();
        
        // Go back to lobby
        primaryStage.setScene(sceneMap.get("client"));
        findGameBtn.setDisable(false);
        findGameBtn.setText("Find a Game");
    }
    

}