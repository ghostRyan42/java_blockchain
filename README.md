# Java Blockchain

A distributed blockchain implementation in Java with peer-to-peer networking, transaction support, and a modern web interface.

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Usage](#usage)
- [API Documentation](#api-documentation)
- [Project Structure](#project-structure)
- [How It Works](#how-it-works)

## 🔍 Overview

This project implements a complete blockchain system in Java with the following components:
- Distributed peer-to-peer network
- Transaction management with digital signatures
- Block mining and validation
- Web-based dashboard interface
- Real-time blockchain synchronization

## ✨ Features

- **Peer-to-Peer Network**: Nodes can connect and communicate with each other to form a decentralized network
- **Transaction System**: Create and broadcast transactions with digital signatures
- **Block Mining**: Automatic block creation when transaction threshold is reached
- **Wallet Management**: Each node has a wallet with public/private key pairs
- **Web Dashboard**: Modern, responsive interface to monitor and interact with the blockchain
- **Real-time Updates**: Live updates of blockchain state, transactions, and network peers
- **Transaction Validation**: Cryptographic signature verification for all transactions
- **Blockchain Validation**: Ensures chain integrity through hash verification

## 🏗️ Architecture

The system consists of several key components:

### Core Classes

- **Block**: Represents a block in the blockchain containing transactions, timestamp, and hash
- **Blockchain**: Manages the chain of blocks and validates block additions
- **Transaction**: Represents a financial transaction with sender, recipient, amount, and fee
- **Wallet**: Manages public/private key pairs and creates signed transactions
- **SignatureUtil**: Provides cryptographic functions (SHA-256, key generation, signatures)

### Network Components

- **NodeServer**: Main server node that listens for connections and manages the HTTP API
- **NodeClient**: Client node that connects to the network
- **NodeMainInfo**: Stores information about connected peers

## 📦 Prerequisites

- Java Development Kit (JDK) 8 or higher
- Gson library (included in `modules/gson-2.10.1.jar`)
- A modern web browser (for the dashboard)

## 🚀 Installation

1. Clone the repository:
```bash
git clone https://github.com/ghostRyan42/java_blockchain.git
cd java_blockchain
```

2. Compile the Java files:
```bash
# Compile the core classes
javac -d . -cp .:modules/gson-2.10.1.jar classes/*.java

# Compile the node classes
javac -cp .:modules/gson-2.10.1.jar NodeServer.java
javac -cp .:modules/gson-2.10.1.jar NodeClient.java
javac -cp .:modules/gson-2.10.1.jar NodeClient3.java
```

## 💻 Usage

### Starting the Network

#### Start the Server Node (Node 1)

```bash
java -cp .:modules/gson-2.10.1.jar NodeServer
```

This will:
- Start a server on port 8080
- Initialize the blockchain with a genesis block
- Start an HTTP server on port 8083
- Open the web dashboard at `http://localhost:8083`

#### Start Client Nodes (Node 2 and Node 3)

In separate terminal windows:

```bash
# Node 2
java -cp .:modules/gson-2.10.1.jar NodeClient
# HTTP server will run on port 5000
# Dashboard: http://localhost:5000

# Node 3
java -cp .:modules/gson-2.10.1.jar NodeClient3
# HTTP server will run on a different port
```

### Connecting Nodes

1. Open the web dashboard in your browser
2. Click "Se Connecter au réseau" (Connect to Network)
3. Enter the host and port of another node (e.g., `localhost:8080`)
4. Click "Se connecter" (Connect)

### Creating Transactions

1. Ensure your node is connected to at least one peer
2. Click "Nouvelle Transaction" (New Transaction)
3. Select a peer from the dropdown
4. Enter the amount to send
5. Click "Envoyer" (Send)

Note: Transactions are automatically mined into blocks when 3 transactions are pending.

## 📚 API Documentation

### Node Information
```
GET /node/info
```
Returns node ID, port, and wallet balance.

**Response:**
```json
{
  "nodeId": "node1",
  "port": 8080,
  "balance": 200.0
}
```

### Connect to Peer
```
POST /node/connect
Content-Type: application/x-www-form-urlencoded

host=localhost&port=8081
```
Connects the node to another peer in the network.

### Create Transaction
```
POST /transaction/create
Content-Type: application/x-www-form-urlencoded

peerWalletKey=<PUBLIC_KEY>&amount=10.0&fee=1.0
```
Creates and broadcasts a new transaction.

### Get Blockchain
```
GET /blockchain
```
Returns the complete blockchain with all blocks and transactions.

**Response:**
```json
[
  {
    "index": 0,
    "timestamp": 1234567890,
    "transactions": [],
    "previousHash": null,
    "hash": "abc123...",
    "nonce": 12345
  }
]
```

### Get Connected Peers
```
GET /node/peers
```
Returns the number of connected peers and their information.

### Get Pending Transactions
```
GET /node/pending-transactions
```
Returns all transactions waiting to be mined into a block.

## 📁 Project Structure

```
java_blockchain/
├── classes/                    # Core blockchain classes
│   ├── Block.java             # Block structure and mining
│   ├── Blockchain.java        # Blockchain management
│   ├── Transaction.java       # Transaction structure
│   ├── Wallet.java            # Wallet and key management
│   ├── SignatureUtil.java     # Cryptographic utilities
│   ├── NodeMainInfo.java      # Peer information
│   └── Message.java           # Message structure
├── modules/                    # External dependencies
│   └── gson-2.10.1.jar        # JSON library
├── NodeServer.java            # Server node implementation
├── NodeClient.java            # Client node implementation
├── NodeClient3.java           # Additional client node
├── index.html                 # Main web dashboard
├── sharable_base.html         # Base dashboard template
├── sharable_first.html        # First node dashboard
├── sharable_second.html       # Second node dashboard
└── README.md                  # This file
```

## ⚙️ How It Works

### Block Creation and Mining

1. Transactions are created and signed with the sender's private key
2. Transactions are broadcast to all connected peers
3. Each node maintains a pool of pending transactions
4. When 3 transactions accumulate, a new block is automatically created
5. The block is added to the local blockchain
6. The new block is broadcast to all peers

### Blockchain Synchronization

1. When a new node joins the network, it receives the complete blockchain from a peer
2. Nodes validate incoming blocks before adding them to their chain
3. Validation includes:
   - Checking that the previous hash matches
   - Verifying the block's hash is correct
   - Ensuring transaction signatures are valid

### Transaction Flow

1. **Creation**: A wallet creates a transaction with recipient, amount, and fee
2. **Signing**: The transaction is signed with the sender's private key
3. **Broadcasting**: The transaction is sent to all connected peers
4. **Pooling**: Each node adds the transaction to its pending pool
5. **Mining**: When enough transactions accumulate, they're mined into a block
6. **Confirmation**: The block is added to the blockchain and broadcast

### Security Features

- **Digital Signatures**: All transactions are signed with ECDSA
- **Hash Verification**: Each block's hash links to the previous block
- **Chain Validation**: The blockchain integrity is constantly verified
- **Balance Tracking**: Wallets maintain balances to prevent overspending

## 🎨 Web Dashboard

The web dashboard provides:
- Real-time wallet balance display
- Network status and connected peers count
- Blockchain visualization with all blocks
- Pending transactions monitoring
- Transaction history
- Interactive forms for connecting to peers and creating transactions
- Live notifications for network events

## 🔐 Security Notes

- This is an educational implementation and should not be used in production
- Private keys are stored in memory and are lost when the node stops
- No persistent storage is implemented
- The proof-of-work difficulty is minimal (4 leading zeros)

## 🤝 Contributing

Contributions are welcome! Feel free to submit issues or pull requests.

## 📄 License

This project is open source and available for educational purposes.

## 👤 Author

ghostRyan42

---

**Note**: This is an educational project to demonstrate blockchain concepts and peer-to-peer networking in Java.
