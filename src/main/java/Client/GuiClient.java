package Client;

import java.util.HashMap;

import common.Message;

import java.util.ArrayList;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class GuiClient extends Application{

	
	TextField c1;
	Button b1;
	HashMap<String, Scene> sceneMap;
	VBox clientBox;
	Client clientConnection;
	
	ListView<String> listItems2;
	ListView<String> userList;
	
	String selectedTarget = "ALL";
	String myUsername;
	
	
	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		sceneMap = new HashMap<String, Scene>();

	    listItems2 = new ListView<String>();
	    userList = new ListView<String>(); 
	    c1 = new TextField();
		b1 = new Button("Send");
		
		Label loginPrompt = new Label("Create a username:");
		Label errorLabel = new Label();
		//loginPrompt.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: black;");
		errorLabel.setStyle("-fx-text-fill: red;");
		errorLabel.setVisible(false);
		
		//username connection
		TextField usernameText = new TextField();
		usernameText.setPromptText("Type username here...");
		Button connectButton = new Button("Connect to Server");
		
		VBox loginBox = new VBox(10, loginPrompt, usernameText, connectButton, errorLabel);
		loginBox.setPadding(new Insets(20));
		
		Scene loginScene = new Scene(loginBox, 300, 200);
		sceneMap.put("login", loginScene);
		
		//UI
		userList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

	    clientConnection = new Client(data -> {
	        Message m = (Message) data;
	        Platform.runLater(() -> {
	            if (m.messageType == 1) {
		            if (m.isNameValid) {
		            myUsername = m.username;
		            primaryStage.setScene(sceneMap.get("client"));
		            errorLabel.setVisible(false);
		            }
		            else {
		            	errorLabel.setText(m.messageText != null ? m.messageText : "Username already taken!");
		            	errorLabel.setVisible(true);
		            }
		        }
	            else if(m.messageType == 3) {
	            	userList.getItems().clear();
	                userList.getItems().addAll(m.users);
	            }
	            else if(m.messageType == 5) {
	            	primaryStage.setScene(sceneMap.get("gameBoard"));
	            }
	            else {
	            	String prefix = "";
	            	if (m.target != null && m.target.equals("Group")) {
	                    String groupList = String.join(", ", m.users);
	                    prefix = "[Group with " + groupList + "] ";
	                }else if (m.target != null && !m.target.equalsIgnoreCase("All")) {
	                    prefix = "[Private] ";
	                }
	                String sender = (m.username != null) ? m.username : "Server";

	                listItems2.getItems().add(prefix + sender + ": " + m.messageText);
	            }
	        });
	    });
	    
	    clientConnection.start();

		//button action
		connectButton.setOnAction(e -> {
			Message login = new Message();
			login.username = usernameText.getText();
			login.messageType = 1; // login attempt
			clientConnection.send(login);
		});
		
		//changed the button because ti was expected a string so we have to change to messages
		b1.setOnAction(e->{
			Message message = new Message();
			message.messageText = c1.getText();
			message.messageType = 2;
//			message.target = selectedTarget;
			ObservableList<String> selectedIndices = userList.getSelectionModel().getSelectedItems();
			
			if(selectedIndices.isEmpty()) {
				message.target = "All";
			} else {
				message.users = new ArrayList<String>(selectedIndices);
				
				if(message.users.size() == 1) {
					message.target = message.users.get(0);
				}else {
					message.target = "Group";
				}
			}
			clientConnection.send(message);	
			c1.clear();
			
		});
		
		//set up scene 
		sceneMap.put("client", createLobbyScene(primaryStage));
		
		//username box
		//VBox loginBox = new VBox(10, loginPrompt, usernameText, connectButton);
		
		//scene
		//Scene loginScene = new Scene(loginBox, 300, 200);
		sceneMap.put("login", loginScene);	
		sceneMap.put("gameBoard", createGameScene());
		
		primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent t) {
                Platform.exit();
                System.exit(0);
            }
        });


		primaryStage.setScene(sceneMap.get("login"));
		primaryStage.setTitle("Client Login");
		primaryStage.show();
		
	}
	

	
	// Inside your createClientGui() or wherever you build the Lobby
	public Scene createLobbyScene(Stage primaryStage) {
	    Button findGameBtn = new Button("Find a Game");
	    findGameBtn.setStyle("-fx-font-size: 16px; -fx-padding: 10 20 10 20;");
	  
	    findGameBtn.setOnAction(e -> {
	        Message matchmakingReq = new Message();
	        matchmakingReq.messageType = 4;
	        matchmakingReq.username = myUsername;
	        clientConnection.send(matchmakingReq);
	        findGameBtn.setDisable(true);
	        findGameBtn.setText("Waiting for Opponent...");
	    });

	    Button logoutBtn = new Button("Logout");
	    logoutBtn.setOnAction(e -> {
	        primaryStage.setScene(sceneMap.get("login"));
	    });

	    VBox leftSide = new VBox(20, new Label("Welcome, " + myUsername), findGameBtn, logoutBtn);
	    VBox rightSide = new VBox(10, new Label("Online Players:"), userList);
	    
	    HBox mainLayout = new HBox(30, leftSide, rightSide);
	    mainLayout.setPadding(new Insets(20));
	    mainLayout.setStyle("-fx-background-color: #e0f7fa;");

	    return new Scene(mainLayout, 600, 400);
	}
	
	public Scene createGameScene() {
	    GridPane board = new GridPane();
	    
	    // Create the 8x8 Checkers board 
	    for (int row = 0; row < 8; row++) {
	        for (int col = 0; col < 8; col++) {
	            Button cell = new Button();
	            cell.setPrefSize(50, 50);
	            
	            // Alternate colors for the board squares 
	            if ((row + col) % 2 == 0) {
	                cell.setStyle("-fx-background-color: white; -fx-border-color: black;");
	            } else {
	                cell.setStyle("-fx-background-color: black; -fx-border-color: black;");
	            }
	            board.add(cell, col, row);
	        }
	    }

	    // Chat Area - Requirement (f) [cite: 38]
	    VBox gameChat = new VBox(10, new Label("Game Chat:"), listItems2, new HBox(5, c1, b1));
	    gameChat.setPadding(new Insets(10));

	    HBox gameLayout = new HBox(20, board, gameChat);
	    gameLayout.setPadding(new Insets(20));
	    gameLayout.setStyle("-fx-background-color: grey;");

	    return new Scene(gameLayout, 800, 600);
	}
	
}
