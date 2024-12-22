import java.io.*;
import java.net.*;

public class ServerSlave {
    private static int PORT;
    private static String STORAGE_PATH;

    public static void main(String[] args) {
        // Charger la configuration
        loadConfig();

        File storageDir = new File(STORAGE_PATH + PORT);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur esclave en écoute sur le port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                new ClientHandler(socket, storageDir).start();
            }
        } catch (IOException e) {
            System.err.println("Erreur serveur esclave: " + e.getMessage());
        }
    }

    private static void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader("config.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("SLAVE_PORT")) {
                    PORT = Integer.parseInt(line.split("=")[1].trim());
                } else if (line.startsWith("SERVER_SLAVE_STORAGE_PATH")) {
                    STORAGE_PATH = line.split("=")[1].trim();
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du chargement de la configuration : " + e.getMessage());
        }
    }

    // Classe pour gérer chaque client connecté au serveur esclave
    private static class ClientHandler extends Thread {
        private Socket socket;
        private File storageDir;

        public ClientHandler(Socket socket, File storageDir) {
            this.socket = socket;
            this.storageDir = storageDir;
        }

        @Override
        public void run() {
            try (InputStream inputStream = socket.getInputStream();
                 OutputStream outputStream = socket.getOutputStream()) {

                DataInputStream dataInputStream = new DataInputStream(inputStream);
                DataOutputStream dataOutputStream = new DataOutputStream(outputStream);

                String action = dataInputStream.readUTF();

                if (action.equals("send")) {
                    handleReceiveFile(dataInputStream);
                } else if (action.equals("get")) {
                    handleSendFile(dataInputStream, dataOutputStream);
                } else if (action.equals("delete")) {
                    handleDeleteFile(dataInputStream, dataOutputStream);
                } else if (action.equals("list")) {
                    handleListFiles(dataOutputStream);
                }

            } catch (IOException e) {
                System.err.println("Erreur de traitement du client sur serveur esclave: " + e.getMessage());
            }
        }

        // Gère la réception et le stockage des fichiers envoyés par le serveur principal
        private void handleReceiveFile(DataInputStream dataInputStream) throws IOException {
            String filename = dataInputStream.readUTF();
            int partNumber = dataInputStream.readInt();
            long partSize = dataInputStream.readLong();
            byte[] partData = new byte[(int) partSize];
            dataInputStream.readFully(partData);

            File file = new File(storageDir, filename + ".part" + partNumber);
            try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                fileOutputStream.write(partData);
            }
        }

        // Gère l'envoi d'un fichier complet au client
        private void handleSendFile(DataInputStream dataInputStream, DataOutputStream dataOutputStream) throws IOException {
            String filename = dataInputStream.readUTF();
            File file = new File(storageDir, filename);
            if (file.exists()) {
                dataOutputStream.writeUTF("found");
                dataOutputStream.writeLong(file.length());
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                        dataOutputStream.write(buffer, 0, bytesRead);
                    }
                }
            } else {
                dataOutputStream.writeUTF("not found");
            }
        }

        private void handleDeleteFile(DataInputStream dataInputStream, DataOutputStream dataOutputStream) throws IOException {
            String baseName = dataInputStream.readUTF(); // Nom de base du fichier (ex. "test.txt")
            boolean allDeleted = true; // Indique si toutes les suppressions ont réussi
            boolean atLeastOneFound = false; // Indique si au moins un fichier a été trouvé
        
            // Lister les fichiers dans le répertoire de stockage
            File[] files = storageDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    // Vérifie si le fichier correspond au nom de base
                    if (file.getName().startsWith(baseName)) {
                        atLeastOneFound = true; // Au moins un fichier trouvé
                        System.out.println("Suppression du fichier : " + file.getAbsolutePath());
                        if (!file.delete()) {
                            System.out.println("Erreur lors de la suppression du fichier : " + file.getAbsolutePath());
                            allDeleted = false; // Échec si un fichier ne peut pas être supprimé
                        }
                    }
                }
            }
        
            // Envoyer la réponse au serveur principal
            if (atLeastOneFound) {
                if (allDeleted) {
                    System.out.println("Tous les fichiers liés à " + baseName + " ont été supprimés avec succès.");
                    dataOutputStream.writeUTF("Fichiers supprimés avec succès.");
                } else {
                    System.out.println("Certaines parties de " + baseName + " n'ont pas pu être supprimées.");
                    dataOutputStream.writeUTF("Échec de la suppression de certains fichiers.");
                }
            } else {
                System.out.println("Aucun fichier trouvé correspondant au nom de base : " + baseName);
                dataOutputStream.writeUTF("Aucun fichier trouvé.");
            }
        }
        

        // Liste les fichiers présents sur le serveur esclave
        private void handleListFiles(DataOutputStream dataOutputStream) throws IOException {
            File[] files = storageDir.listFiles();
            dataOutputStream.writeInt(files != null ? files.length : 0);
            if (files != null) {
                for (File file : files) {
                    dataOutputStream.writeUTF(file.getName());
                }
            }
        }
    }
}
