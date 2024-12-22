import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    private static String SERVER_ADDRESS;
    private static int SERVER_PORT;

    public static void main(String[] args) {
        loadConfig();

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\nMenu :");
            System.out.println("1. Envoyer un fichier");
            System.out.println("2. Telecharger un fichier");
            System.out.println("3. Supprimer un fichier");
            System.out.println("4. Lister les fichiers disponibles");
            System.out.println("5. Quitter");
            System.out.print("Choisissez une option : ");
            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1:
                    sendFile(scanner);
                    break;
                case 2:
                    downloadFile(scanner);
                    break;
                case 3:
                    deleteFile(scanner);
                    break;
                case 4:
                    listFiles();
                    break;
                case 5:
                    System.out.println("Au revoir !");
                    return;
                default:
                    System.out.println("Option invalide. Essayez a nouveau.");
            }
        }
    }

    private static void loadConfig() {
        try (BufferedReader reader = new BufferedReader(new FileReader("config.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("MAIN_SERVER_IP")) {
                    SERVER_ADDRESS = line.split("=")[1].trim();
                } else if (line.startsWith("MAIN_SERVER_PORT")) {
                    SERVER_PORT = Integer.parseInt(line.split("=")[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur lors du chargement de la configuration : " + e.getMessage());
        }
    }

    // Envoie un fichier au serveur principal
    private static void sendFile(Scanner scanner) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {

            System.out.print("Entrez le chemin du fichier a envoyer : ");
            String filePath = scanner.nextLine();
            File file = new File(filePath);

            if (!file.exists()) {
                System.out.println("Fichier non trouve.");
                return;
            }

            try (FileInputStream fileInputStream = new FileInputStream(file)) {
                byte[] fileBytes = new byte[(int) file.length()];
                fileInputStream.read(fileBytes);

                dataOutputStream.writeUTF("send");
                dataOutputStream.writeUTF(file.getName());
                dataOutputStream.writeLong(file.length());
                dataOutputStream.write(fileBytes);
            }
            String response = dataInputStream.readUTF();
            System.out.println(response);

        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoi du fichier : " + e.getMessage());
        }
    }

    // Telecharge un fichier depuis le serveur principal
    private static void downloadFile(Scanner scanner) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {

            System.out.print("Entrez le nom du fichier a telecharger : ");
            String fileName = scanner.nextLine();

            dataOutputStream.writeUTF("get");
            dataOutputStream.writeUTF(fileName);

            long fileSize = dataInputStream.readLong();
            if (fileSize == 0) {
                System.out.println("Fichier introuvable.");
            } else {
                byte[] fileBytes = new byte[(int) fileSize];
                dataInputStream.readFully(fileBytes);

                try (FileOutputStream fileOutputStream = new FileOutputStream("download/" + fileName)) {
                    fileOutputStream.write(fileBytes);
                }
                System.out.println("Fichier telecharge avec succes.");
            }

        } catch (IOException e) {
            System.err.println("Erreur lors du telechargement du fichier : " + e.getMessage());
        }
    }

    // Supprime un fichier
    private static void deleteFile(Scanner scanner) {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {

            System.out.print("Entrez le nom du fichier a supprimer : ");
            String fileName = scanner.nextLine();

            dataOutputStream.writeUTF("delete");
            dataOutputStream.writeUTF(fileName);

            String response = dataInputStream.readUTF();
            System.out.println(response);

        } catch (IOException e) {
            System.err.println("Erreur lors de la suppression du fichier : " + e.getMessage());
        }
    }

    // Liste les fichiers disponibles
    private static void listFiles() {
        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             DataOutputStream dataOutputStream = new DataOutputStream(socket.getOutputStream());
             DataInputStream dataInputStream = new DataInputStream(socket.getInputStream())) {

            dataOutputStream.writeUTF("list");

            int fileCount = dataInputStream.readInt();
            if (fileCount == 0) {
                System.out.println("Aucun fichier disponible.");
            } else {
                System.out.println("Fichiers disponibles :");
                for (int i = 0; i < fileCount; i++) {
                    String filename = dataInputStream.readUTF();
                    System.out.println(filename);
                }
            }

        } catch (IOException e) {
            System.err.println("Erreur lors de la liste des fichiers : " + e.getMessage());
        }
    }
}
