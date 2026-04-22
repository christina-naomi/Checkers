//Client
package common;

import java.io.Serializable;
import java.util.ArrayList; //for arrays

public class Message implements Serializable {
    static final long serialVersionUID = 42L;
    
    public String messageText;
    public String username;
    public String target;
    public int messageType;
    public boolean isNameValid;
    public String errorMessage;
    public ArrayList<String> users;
    
  //game syncing
    public int fromRow, fromCol;
    public int toRow, toCol;
    public boolean wasMyMove;
}
