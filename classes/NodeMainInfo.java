import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.security.PublicKey;

public class NodeMainInfo implements Serializable {

    private String ipAddress; // L'adresse IP du nœud
    private int port; // Le port sur lequel le nœud écoute
    private PublicKey walletPublicKey; // La clé publique du portefeuille
    public int nodeId;
    public static int compteur=0;
    private transient ObjectOutputStream outputStream;
    // public transient ObjectOutputStream out = null;
    private transient ObjectInputStream inputStream;

    // Constructeur
    public NodeMainInfo(String ipAddress, int port, PublicKey walletPublicKey) {
        this.ipAddress = ipAddress;
        this.port = port;
        this.walletPublicKey = walletPublicKey;
        synchronized (NodeMainInfo.class) {
            compteur += 1;
            this.nodeId = compteur; // Génère un identifiant unique
        }
    }

    // Getters et setters
    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public PublicKey getWalletPublicKey() {
        return walletPublicKey;
    }

    public void setWalletPublicKey(PublicKey walletPublicKey) {
        this.walletPublicKey = walletPublicKey;
    }

    public void setOut(ObjectOutputStream out) {
        this.outputStream = out;
    }

    public ObjectOutputStream getOutputStream() {
        return this.outputStream;
    }

    public void setIn(ObjectInputStream in) {
        this.inputStream = in;
    }

    public ObjectInputStream getInputStream() {
        return this.inputStream;
    }

    @Override
    public String toString() {
        return "NodeMainInfo [ipAddress=" + ipAddress + ", port=" + port + ", walletPublicKey=" + walletPublicKey + "]";
    }
}
