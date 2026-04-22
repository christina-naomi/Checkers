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
/*
 * Clicker: A: I really get it    B: No idea what you are talking about
 * C: kind of following
 */

public class Server{

	int count = 1;	
	ArrayList<ClientThread> clients = new ArrayList<ClientThread>();
	ArrayList<ClientThread> matchmakingQueue = new ArrayList<>();
	TheServer server;
	private Consumer<Serializable> callback;
	
	
	Server(Consumer<Serializable> call){
	
		callback = call;
		server = new TheServer();
		server.start();
	}
	
	//new method
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
	
	
	public class TheServer extends Thread{
		public void run() {
			try(ServerSocket mysocket = new ServerSocket(5555);){
		    System.out.println("Server is waiting for a client!");
		    while(true) {
				ClientThread c = new ClientThread(mysocket.accept(), count);
				callback.accept("client has connected to server: " + "client #" + count);
				clients.add(c);
				c.start();
				count++;
				
			    }
			}catch(Exception e) {
				callback.accept("Server socket did not launch");
			}
		}
	}
	
///THIS IS CLIENT THREAD OMG ITS SO HARRD TO FIND
/// CLIENT THREAD
/// CLIENT THREAD
/// CLIENT THREAD
/// CLIENT THERAD

		class ClientThread extends Thread{
			Socket connection;
			int count;
			ObjectInputStream in;
			ObjectOutputStream out;
			String threadUsername;
			
			ClientThread(Socket s, int count){
				this.connection = s;
				this.count = count;	
			}
			
			public void updateClients(Message message) {
				for(int i = 0; i < clients.size(); i++) {
					ClientThread t = clients.get(i);
					try {
					 t.out.writeObject(message);
					}
					catch(Exception e) {}
				}
			}
			
			public void run(){
					
				try {
					in = new ObjectInputStream(connection.getInputStream());
					out = new ObjectOutputStream(connection.getOutputStream());
					connection.setTcpNoDelay(true);	
				}
				catch(Exception e) {
					System.out.println("Streams not open");
				}
				
				Message welcomeMessage = new Message();
				welcomeMessage.messageText = "new client on server: client #" + count;
				updateClients(welcomeMessage);
					
				 while(true) {
					    try {
					    	Message data = (Message) in.readObject(); // no convert to string
					    	//check if this is the login attempt
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
					                //data.messageText = this.threadUsername + " has joined the chat!";
					                out.writeObject(data); 
					                out.reset();
					                showUserList();
					    		}
					    	}
					    	else if (data.messageType == 4) {
		                        synchronized (matchmakingQueue) {
		                            if (!matchmakingQueue.contains(this)) {
		                                matchmakingQueue.add(this);
		                                callback.accept(threadUsername + " joined matchmaking queue.");
		                            }

		                            if (matchmakingQueue.size() >= 2) {
		                                // Randomly pair two users 
		                            	ClientThread p1 = matchmakingQueue.remove(0);
		                                ClientThread p2 = matchmakingQueue.remove(0);

		                                Message startMatch = new Message();
		                                startMatch.messageType = 5;
		                                // Put both names in the message
		                                startMatch.username = p1.threadUsername; 
		                                startMatch.target = p2.threadUsername; // Using 'target' to store the second name

		                                p1.out.writeObject(startMatch);
		                                p2.out.writeObject(startMatch);
		                                p2.out.reset();

		                                callback.accept("Match started: " + p1.threadUsername + " vs " + p2.threadUsername);
		                            }
		                        }
		                    }
					    	else if(data.messageType == 6) {
					    	    // A player moved a piece. Broadcast this move to all clients
					    	    // so the opponent's board updates.
					    		callback.accept("Move recorded from " + threadUsername + 
					                    ": [" + data.fromRow + "," + data.fromCol + "] to [" + 
					                    data.toRow + "," + data.toCol + "]");
					    
							    // Send to ALL clients so both can update their boards
							    // But add a flag to indicate who made the move
							    data.username = threadUsername;
							    updateClients(data); 
					    	}
					    	else if(data.messageType == 7) {
					    		callback.accept(threadUsername + " has quit the game. Ending match.");
					    	    updateClients(data);
					    	}
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
					    	    
					    	    // This is the key: setting this to null makes the name 
					    	    // available again in your Type 1 (Login) check loop!
					    	    this.threadUsername = null; 
					    	    
					    	    // Update the online players list for everyone else
					    	    showUserList();
					    	}
					    	else { //CHAT
					    		data.username = this.threadUsername;
					    		
					    		if(data.target == null || data.target.equals("All")) {
					    			//callback.accept("client: " + threadUsername + " sent a message");
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
					    			//callback.accept(threadUsername + " to " + data.target + ": " + data.messageText);
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
					    catch(Exception e) {
					    	clients.remove(this);
					    	callback.accept("OOOOPPs...Something wrong with the socket from client: " + count + "....closing down!");
					    	//clients.remove(this);
					    	
//					    	Message byeMessage = new Message();
//					    	byeMessage.messageText = "Client #" + count + " has left the server!";
//					    	updateClients(byeMessage);
//					    	
					    	showUserList();
					    	break;
					    }
					}
				}//end of run
			
			
		}//end of client thread
}

