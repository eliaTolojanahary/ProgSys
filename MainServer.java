import java.io.*;
import java.net.*;
import java.util.*;

public class MainServer {
    private static String[] SERVER_SLAVES = new String[3];
    private static int SLAVE_PORT;
    private static String MAIN_SERVER_IP;
    private static int MAIN_SERVER_PORT;

    public static void main(String[] args) {
        // Charger la configuration
        loadConfig();

        try (ServerSocket serverSocket = new ServerSocket(MAIN_SERVER_PORT, 50, InetAddress.getByName(MAIN_SERVER_IP))) {
            System.out.println("Serveur principal en écoute sur le port " + MAIN_SERVER_PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur serveur principal: " + e.getMessage());
        }
    }

    private static void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader("config.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("SLAVE1_IP")) {
                    SERVER_SLAVES[0] = line.split("=")[1].trim();
                } else if (line.startsWith("SLAVE2_IP")) {
                    SERVER_SLAVES[1] = line.split("=")[1].trim();
                } else if (line.startsWith("SLAVE3_IP")) {
                    SERVER_SLAVES[2] = line.split("=")[1].trim();
                } else if (line.startsWith("SLAVE_PORT")) {
                    SLAVE_PORT = Integer.parseInt(line.split("=")[1].trim());
                } else if (line.startsWith("MAIN_SERVER_IP")) {
                    MAIN_SERVER_IP = line.split("=")[1].trim();
                } else if (line.startsWith("MAIN_SERVER_PORT")) {
                    MAIN_SERVER_PORT = Integer.parseInt(line.split("=")[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du chargement de la configuration : " + e.getMessage());
        }
    }

    // Classe pour gérer chaque client connecté
    private static class ClientHandler extends Thread {
        private Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (InputStream inputStream = socket.getInputStream();
                 OutputStream outputStream = socket.getOutputStream()) {

                DataInputStream dataInputStream = new DataInputStream(inputStream);
                DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

                String action = dataInputStream.readUTF(); // Action demandée par le client (envoyer, télécharger, supprimer, etc.)

                if (action.equals("send")) {
                    handleSendFile(dataInputStream, dataOutputStream);
                } else if (action.equals("get")) {
                    handleGetFile(dataInputStream, dataOutputStream);
                } else if (action.equals("delete")) {
                    handleDeleteFile(dataInputStream, dataOutputStream);
                } else if (action.equals("list")) {
                    handleListFiles(dataOutputStream);
                }

            } catch (IOException e) {
                System.err.println("Erreur de traitement du client sur serveur principal: " + e.getMessage());
            }
        }

        // Gère l'envoi du fichier du client vers les serveurs esclaves
        private void handleSendFile(DataInputStream dataInputStream, DataOutputStream dataOutputStream) throws IOException {
            String filename = dataInputStream.readUTF();
            long fileSize = dataInputStream.readLong();
            byte[] fileBytes = new byte[(int) fileSize];
            dataInputStream.readFully(fileBytes);

            // Diviser le fichier en trois parties
            int partSize = (int) Math.ceil(fileSize / 3.0);
            for (int i = 0; i < 3; i++) {
                int start = i * partSize;
                int end = Math.min(start + partSize, fileBytes.length);
                byte[] partData = Arrays.copyOfRange(fileBytes, start, end);

                // Envoyer chaque partie au serveur esclave
                try (Socket slaveSocket = new Socket(SERVER_SLAVES[i], SLAVE_PORT);
                     DataOutputStream slaveOutputStream = new DataOutputStream(slaveSocket.getOutputStream())) {
                    slaveOutputStream.writeUTF("send");
                    slaveOutputStream.writeUTF(filename);
                    slaveOutputStream.writeInt(i + 1); // Numéro de la partie
                    slaveOutputStream.writeLong(partData.length);
                    slaveOutputStream.write(partData);
                }
            }

            dataOutputStream.writeUTF("Fichier envoyé et partitionné.");
        }

        // Gère le téléchargement et l'assemblage du fichier
        private void handleGetFile(DataInputStream dataInputStream, DataOutputStream dataOutputStream) throws IOException {
            String baseName = dataInputStream.readUTF();
            byte[] fullFile = new byte[0];
            boolean fileFound = false;

            for (int i = 1; i <= 3; i++) {
                // Demander la partie à chaque serveur esclave
                try (Socket slaveSocket = new Socket(SERVER_SLAVES[i - 1], SLAVE_PORT);
                     DataOutputStream slaveOutputStream = new DataOutputStream(slaveSocket.getOutputStream());
                     DataInputStream slaveInputStream = new DataInputStream(slaveSocket.getInputStream())) {

                    slaveOutputStream.writeUTF("get");
                    slaveOutputStream.writeUTF(baseName + ".part" + i);

                    String response = slaveInputStream.readUTF();
                    if (response.equals("found")) {
                        fileFound = true;
                        long partSize = slaveInputStream.readLong();
                        byte[] partData = new byte[(int) partSize];
                        slaveInputStream.readFully(partData);
                        fullFile = concatenate(fullFile, partData);
                    }
                }
            }

            if (fileFound) {
                dataOutputStream.writeLong(fullFile.length);
                dataOutputStream.write(fullFile);
            } else {
                dataOutputStream.writeLong(0);
            }
        }

        private void handleDeleteFile(DataInputStream dataInputStream, DataOutputStream dataOutputStream) throws IOException {
            String baseName = dataInputStream.readUTF();
            boolean allFilesDeleted = true; // Indique si tous les esclaves ont bien supprimé leurs fichiers
            boolean atLeastOneSuccess = false; // Indique si au moins un esclave a trouvé et supprimé des fichiers
        
            // Parcourir les esclaves pour leur demander de supprimer le fichier
            for (int i = 0; i < SERVER_SLAVES.length; i++) {
                try (Socket slaveSocket = new Socket(SERVER_SLAVES[i], SLAVE_PORT);
                     DataOutputStream slaveOutputStream = new DataOutputStream(slaveSocket.getOutputStream());
                     DataInputStream slaveInputStream = new DataInputStream(slaveSocket.getInputStream())) {
        
                    // Envoyer la commande de suppression
                    slaveOutputStream.writeUTF("delete");
                    slaveOutputStream.writeUTF(baseName);
        
                    // Lire la réponse de l'esclave
                    String response = slaveInputStream.readUTF();
                    System.out.println("Réponse du serveur esclave " + SERVER_SLAVES[i] + ": " + response);
        
                    // Analyser la réponse
                    if (response.equals("Fichiers supprimés avec succès.")) {
                        atLeastOneSuccess = true; // Au moins un esclave a supprimé des fichiers
                    } else if (response.equals("Aucun fichier trouvé.")) {
                        System.out.println("Aucun fichier à supprimer sur le serveur esclave " + SERVER_SLAVES[i]);
                    } else {
                        allFilesDeleted = false; // Si un esclave a échoué, marquer comme échec global
                    }
                } catch (IOException e) {
                    System.err.println("Erreur de communication avec le serveur esclave " + SERVER_SLAVES[i] + ": " + e.getMessage());
                    allFilesDeleted = false;
                }
            }
        
            // Envoyer la réponse finale au client
            if (atLeastOneSuccess && allFilesDeleted) {
                dataOutputStream.writeUTF("Fichiers supprimés avec succès.");
            } else if (atLeastOneSuccess) {
                dataOutputStream.writeUTF("Fichiers partiellement supprimés.");
            } else {
                dataOutputStream.writeUTF("Échec de la suppression : aucun fichier trouvé ou erreur.");
            }
        }
        

        // Liste les fichiers disponibles pour téléchargement
        private void handleListFiles(DataOutputStream dataOutputStream) throws IOException {
            Set<String> fileSet = new HashSet<>();

            for (int i = 1; i <= 3; i++) {
                try (Socket slaveSocket = new Socket(SERVER_SLAVES[i - 1], SLAVE_PORT);
                     DataOutputStream slaveOutputStream = new DataOutputStream(slaveSocket.getOutputStream());
                     DataInputStream slaveInputStream = new DataInputStream(slaveSocket.getInputStream())) {

                    slaveOutputStream.writeUTF("list");
                    int fileCount = slaveInputStream.readInt();

                    for (int j = 0; j < fileCount; j++) {
                        fileSet.add(slaveInputStream.readUTF());
                    }
                }
            }

            dataOutputStream.writeInt(fileSet.size());
            for (String file : fileSet) {
                dataOutputStream.writeUTF(file);
            }
        }

        // Méthode pour concaténer les parties du fichier
        private byte[] concatenate(byte[] a, byte[] b) {
            byte[] result = new byte[a.length + b.length];
            System.arraycopy(a, 0, result, 0, a.length);
            System.arraycopy(b, 0, result, a.length, b.length);
            return result;
        }
    }
}
