package drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import exceptions.FileNotSupportedException;
import exceptions.FileSystemClosedException;
import meta.FileMetaData;
import system.FileSystem;
import system.FileSystemManager;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

import static util.Preconditions.*;

public class DriveFileSystemImplementation implements FileSystem {

    private static final int PORT = 8888;

    private static final String CREDENTIALS_FILE_PATH = "/client_secret.json";

    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    private static final String APPLICATION_NAME = "sk-drive";

    private static final String MIME_TYPES_DELIMITER = "#";

    private static final File MIME_TYPES_FILE = new File("src/main/resources/mime_types.txt");

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);

    static {
        FileSystemManager.registerSystem(new DriveFileSystemImplementation());
    }

    private DriveFileSystemImplementation() {}

    private Drive service;

    @Override
    public void initialize() {
        if (service != null)
            return;

        try {
            final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            service = new Drive.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }

        loadMimeTypes();
    }

    private static Credential getCredentials(final NetHttpTransport httpTransport) throws IOException {
        final InputStream input = DriveFileSystemImplementation.class.getResourceAsStream(CREDENTIALS_FILE_PATH);

        final GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(input));

        final GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        final LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(PORT).build();

        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private final List<String> mimeTypes = new ArrayList<>();

    private void loadMimeTypes() {
        try (final BufferedReader reader = new BufferedReader(new FileReader(MIME_TYPES_FILE))) {
            String mimeType = reader.readLine();

            while (mimeType != null) {
                mimeTypes.add(mimeType);
                mimeType = reader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // method is intended for com.google.api.client.http.FileContent
    // FileContent default value for mime type is null so its safe for
    // this method to return it if no other valid type was found
    private String findMimeType(final File file) {
        final String fileExtension = file.getPath().substring(file.getPath().lastIndexOf("."));

        final String foundType = mimeTypes.stream()
                .filter(mimeType -> compareExtensionWithMimeType(fileExtension, mimeType))
                .findFirst()
                .orElse(null);

        if (foundType == null)
            return null;

        return foundType.split(MIME_TYPES_DELIMITER)[1];
    }

    private boolean compareExtensionWithMimeType(final String fileExtension, final String mimeType) {
        final String[] mimeExtension = mimeType.split(MIME_TYPES_DELIMITER);

        return mimeExtension[0].equals(fileExtension);
    }

    private boolean closed;

    @Override
    public void terminate() {
        if (service == null)
            return;

        closed = true;
        service = null;
    }

    private void validateMethod(final Object... parameters) {
        if (closed)
            throw new FileSystemClosedException("File system closed!");

        checkNotNull(parameters);
    }

    private final List<String> excludedExtensions = new ArrayList<>();

    @Override
    public void excludeFileExtension(final String fileExtension) {
        validateMethod(fileExtension);

        checkArgument(!excludedExtensions.contains(fileExtension),
                String.format("File extension: %s, already excluded", fileExtension));

        excludedExtensions.add(fileExtension);
    }

    private void checkExcluded(final File file) {
        final String fileExtension = file.getPath().substring(file.getPath().lastIndexOf("."));

        if (isExcluded(fileExtension))
            throw new FileNotSupportedException(String.format("File extension: %s, not supported", fileExtension));
    }

    private boolean isExcluded(final String fileExtension) {
       return excludedExtensions.stream().anyMatch(fileExtension::equals);
    }

    @Override
    public void upload(final File file, final String path) {
        validateMethod(file, path);
        checkExcluded(file);

        uploadWorker(file);
    }

    private void uploadWorker(final File file) {
        if (service == null)
            initialize();

        final com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();

        fileMetadata.setName(file.getName());

        final FileContent fileContent = new FileContent(findMimeType(file), file);

        try {
            service.files().create(fileMetadata, fileContent).setFields("id").execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String... args) {
        DriveFileSystemImplementation implementation = new DriveFileSystemImplementation();
        implementation.initialize();

        implementation.excludeFileExtension(".jpg");
        implementation.upload(new File("src/main/resources/upload_test.png"), "asd");
    }


    @Override
    public void upload(File file, String s, FileMetaData fileMetaData) {

    }

    @Override
    public void uploadCollection(Collection<File> collection, String s) {

    }

    @Override
    public void uploadCollection(Map<File, FileMetaData> map, String s) {

    }

    @Override
    public void download(File file) {

    }

    @Override
    public void downloadCollection(Collection<File> list) {

    }

    @Override
    public void createDir(String s) {

    }

    @Override
    public List<File> findAll() {
        return null;
    }

    @Override
    public List<File> findByName(String s) {
        return null;
    }

    @Override
    public List<File> findByExtension(String s) {
        return null;
    }
}
