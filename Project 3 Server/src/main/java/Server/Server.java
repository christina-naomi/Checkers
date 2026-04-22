package Server;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.function.Consumer;

import common.Message;
import javafx.application.Platform;
import javafx.scene.control.ListView;



///Main server class that manages all the client connections and matchmaking
public class Server{

	int count = 1;	//unique ID for each connected client
	ArrayList<ClientThread> clients = new ArrayList<ClientThread>(); //all connected clients
	ArrayList<ClientThread> matchmakingQueue = new ArrayList<>(); //users who are looking for a game
	TheServer server;
	private Consumer<Serializable> callback; //sends status updates to the server GUI
	
	
	Server(Consumer<Serializable> call){
		callback = call;
		server = new TheServer();
		server.start(); //starts to listen for new connections
	}
	
	
	
	///Gets all the active users and sends the updated list to every client. It keeps the 
	/// "Online Players" list in the lobby scene up to date
	/// 
	public void showUserList() {
		ArrayList<String> currentNames = new ArrayList<>();
		for (ClientThread c : clients) {
			if (c.threadUsername != null) {
				currentNames.add(c.threadUsername);
			}
		}
		
		Message listMessage = new Message();
		listMessage.messageType = 3;
		listMessage.users = currentNames;
		
		for (ClientThread c : clients) {
			try {
				c.out.writeObject(listMessage);
				c.out.reset();
			} catch (Exception e) {}
		}
	}
	
	
	///Inner thread class that always runs and listens for new connection requests
	/// 
	public class TheServer extends Thread{
		public void run() {
			try(ServerSocket mysocket = new ServerSocket(5555);){
		    System.out.println("Server is waiting for a client!");
		    while(true) {
		    	// pauses the thread until a client connects
				ClientThread c = new ClientThread(mysocket.accept(), count);
				callback.accept("client has connected to server: " + "client #" + count);
				clients.add(c);
				c.start(); //starts the specific handler
				count++;
				
			    }
			}catch(Exception e) {
				callback.accept("Server socket did not launch");
			}
		}
	}
	
		///Inner thread class to represent each unique connection to a client depending on their ID
		class ClientThread extends Thread{
			Socket connection;
			int count;
			ObjectInputStream in;
			ObjectOutputStream out;
			String threadUsername;
			ClientThread opponent; //IMPORTANT: Tracks the user that this user is playing against so we can keep track of each different game
			
			ClientThread(Socket s, int count){
				this.connection = s;
				this.count = count;	
			}
			
			///Sends a messages to only this player and their opponent, not everyone on the server
			/// Helps so when one game quits the other doesn't quit as well
			/// 
			public void updateMatch(Message message) {
		        try {
		            this.out.writeObject(message);
		            this.out.reset();
		            if (opponent != null) {
		                opponent.out.writeObject(message);
		                opponent.out.reset();
		            }
		        } catch (Exception e) {
		            System.out.println("Error updating match");
		        }
		    }
			
			///Shows a message to everyone on the server and used to welcome new clients joined
			public void updateClients(Message message) {
				for(int i = 0; i < clients.size(); i++) {
					ClientThread t = clients.get(i);
					try {
					 t.out.writeObject(message);
					}
					catch(Exception e) {}
				}
			}
			
			///Moves in server chat were being printed at [1,2] but changed the board to letters and numbers so this method converts
		    /// 
			private String convertToNotation(int row, int col) {
			    char columnLetter = (char) ('A' + col);
			    int rowNumber = 8 - row;
			    return "" + columnLetter + rowNumber;
			}
			
			public void run(){
				//setup input and output streams for the socket
				try {
					in = new ObjectInputStream(connection.getInputStream());
					out = new ObjectOutputStream(connection.getOutputStream());
					connection.setTcpNoDelay(true);	
				}
				catch(Exception e) {
					System.out.println("Streams not open");
				}
				
				//sends initial welcome message
				Message welcomeMessage = new Message();
				welcomeMessage.messageText = "new client on server: client #" + count;
				updateClients(welcomeMessage);
					
				//main loop that waits for messages from the client
				 while(true) {
					    try {
					    	Message data = (Message) in.readObject(); // no convert to string


					    	//---CASE 1: LOGIN ATTEMPT---
					    	if(data.messageType == 1) {
					    		boolean nameExists = false;
					    		
					    		//check all connected clients to see if name is free
					    		for(ClientThread c : clients) {
					    			if(data.username.equals(c.threadUsername)) {
					    				nameExists = true;
					    				break;
					    			}
					    		}
					    		if(nameExists) {
					    			data.messageText = "Error: Username already taken.";
					    			data.isNameValid = false;
					    			out.writeObject(data);
					    			out.reset();
					    		}
					    		else {
					    			this.threadUsername = data.username;
					                data.isNameValid = true;
					                out.writeObject(data); 
					                out.reset();
					                showUserList(); //updates the lobby players list
					    		}
					    	}
					    	
					    	//---CASE 4: JOIN MATCHMAKING QUEUE---
					    	else if (data.messageType == 4) {
		                        synchronized (matchmakingQueue) {
		                            if (!matchmakingQueue.contains(this)) {
		                                matchmakingQueue.add(this);
		                                callback.accept(threadUsername + " joined matchmaking queue.");
		                            }
		                            
		                            //If two people are waiting, then a game starts!
		                            if (matchmakingQueue.size() >= 2) {
		                                // Randomly pair two users 
		                            	ClientThread p1 = matchmakingQueue.remove(0);
		                                ClientThread p2 = matchmakingQueue.remove(0);
		                                
		                                //Link the players as opponents so we can keep track
		                                p1.opponent = p2;
		                                p2.opponent = p1;

		                                Message startMatch = new Message();
		                                startMatch.messageType = 5; //Starts game
		                                startMatch.username = p1.threadUsername; 
		                                startMatch.target = p2.threadUsername;

		                                p1.out.writeObject(startMatch);
		                                p2.out.writeObject(startMatch);
		                                p2.out.reset();

		                                callback.accept("Match started: " + p1.threadUsername + " vs " + p2.threadUsername);
		                            }
		                        }
		                    }
					    	
					    	//---CASE 6: PIECE MOVED---
					    	else if(data.messageType == 6) {
					    		// Create a readable log string using the notation helper
					    	    String readableMove = convertToNotation(data.fromRow, data.fromCol) + 
					    	                          " to " + 
					    	                          convertToNotation(data.toRow, data.toCol);
					    	    // A player moved a piece so we share this move to all clients so the opponent's board updates.
					    		callback.accept("Move recorded from " + threadUsername + ": " + readableMove);
	
							    data.username = threadUsername;
							    updateMatch(data); //only update these two players in this specific game. Not any other games
					    	}
					    	
					    	//---CASE 7: QUIT GAME---
					    	else if(data.messageType == 7) {
					    		callback.accept(threadUsername + " has quit the game.");
					    	    updateMatch(data); //returns the opponent back to the lobby
					    	    
					    	    // Clear the opponent links so they can join new games
					    	    if (opponent != null) {
					    	        opponent.opponent = null;
					    	    }
					    	    this.opponent = null;
					    	}
					    	
					    	//---CASE 8: LOGOUT---
					    	else if(data.messageType == 8) {
					    	    callback.accept(threadUsername + " has logged out.");
					    	    Message logoutAck = new Message();
					    	    logoutAck.messageType = 8;
					    	    logoutAck.username = threadUsername;
					    	    try {
					    	        out.writeObject(logoutAck);
					    	        out.reset();
					    	    } catch (Exception e) {
					    	        e.printStackTrace();
					    	    }
					    	    
					    	    this.threadUsername = null; //free up the name
					    	    showUserList();// Update the online players list for everyone else
					    	}
					    	
					    	//---CASE 9: REMATCH REQUEST---
					    	else if(data.messageType == 9) {
					    	    callback.accept(threadUsername + " requested a rematch");
					    	    Message rematchAck = new Message();
					    	    rematchAck.messageType = 9;
					    	    rematchAck.username = threadUsername;
					    	    updateMatch(rematchAck); //update this specific player and this game, not any other games
					    	}
					    	
					    	//---DEFAULT CASE: CHAT LOGIC---
					    	else {
					    		data.username = this.threadUsername;
					    		if(data.target == null || data.target.equals("All")) {
						    		updateClients(data);	
					    		} else if (data.target.equals("Group")){
					    			String groupList = String.join(", ", data.users);
					    			callback.accept(threadUsername + " to Group (" + groupList + "): " + data.messageText);
					    			
					    			for (ClientThread c : clients) {
		                                if (c.threadUsername != null && data.users.contains(c.threadUsername)) {
		                                	try {
		                                		c.out.writeObject(data);
		                                		c.out.reset();
		                                	} catch (Exception e) {}
		                                }
					    			}
					    			try { out.writeObject(data); out.reset(); } catch (Exception e) {}
					    		}
					    		else {//private message
		                            for (ClientThread c : clients) {
		                                if (c.threadUsername != null && c.threadUsername.equals(data.target)) {
		                                    try {
		                                	c.out.writeObject(data);
		                                    c.out.reset();
		                                    } catch (Exception e) {}
		                                    break;
		                                }
		                            }
		                            try { out.writeObject(data); out.reset(); } catch (Exception e) {}
					    		}
					    	}
					    }
					    catch(Exception e) { //handles disconnections
					    	clients.remove(this);
					    	callback.accept("OOOOPPs...Something wrong with the socket from client: " + count + "....closing down!");			    	
					    	showUserList();
					    	break;
					    }
					}
				}//end of run
			
			
		}//end of client thread
}

