package drive;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import meta.FileMetaData;
import system.FileSystem;
import system.FileSystemManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DriveFileSystemImplementation implements FileSystem {

    private static final int PORT = 8888;

    private static final String CREDENTIALS_FILE_PATH = "/client_secret.json";

    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    private static final String APPLICATION_NAME = "sk-drive";

    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_METADATA_READONLY);

    static {
        FileSystemManager.registerSystem(new DriveFileSystemImplementation());
    }

    private DriveFileSystemImplementation() {}

    private Drive service;

    @Override
    public void initialize() {
        try {
            final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            service = new Drive.Builder(httpTransport, JSON_FACTORY, getCredentials(httpTransport))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
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

    @Override
    public void terminate() {

    }

    @Override
    public void upload(File file, String s) {

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
    public void downloadCollection(List<File> list) {

    }

    @Override
    public void createDir(String s, String s1) {

    }

    @Override
    public void excludeFileExtension(String s) {

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
