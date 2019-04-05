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
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import exceptions.FileNotSupportedException;
import exceptions.FileSystemClosedException;
import meta.FileMetaData;
import system.FileSystem;
import system.FileSystemManager;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

import static util.Preconditions.*;

public class DriveFileSystemImplementation implements FileSystem<File> {

    private static final int PORT = 8888;

    private static final String CREDENTIALS_FILE_PATH = "/client_secret.json";

    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    private static final String APPLICATION_NAME = "sk-drive";

    private static final String MIME_TYPES_DELIMITER = "#";

    private static final String MIME_FILE_PATH = "src/main/resources/mime_types.txt";

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
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        final LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(PORT).build();

        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private final List<String> mimeTypes = new ArrayList<>();

    private void loadMimeTypes() {
        try (final BufferedReader reader = new BufferedReader(new FileReader(MIME_FILE_PATH))) {
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
    private String findMimeType(final String filePath) {
        final String fileExtension = filePath.substring(filePath.lastIndexOf("."));

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

    private void checkExcluded(final String filePath) {
        if (!filePath.contains("."))
            throw new FileNotSupportedException(String.format("File path: %s, not supported", filePath));

        final String fileExtension = filePath.substring(filePath.lastIndexOf("."));

        if (isExcluded(fileExtension))
            throw new FileNotSupportedException(String.format("File extension: %s, not supported", fileExtension));
    }

    private boolean isExcluded(final String fileExtension) {
       return excludedExtensions.stream().anyMatch(fileExtension::equals);
    }

    @Override
    public void upload(final String filePath, final String path) {
        validateMethod(filePath, path);
        checkExcluded(filePath);

        uploadWorker(filePath, path, null);
    }

    @Override
    public void upload(final FileMetaData fileMetadata, final String path) {
        validateMethod(path, fileMetadata);
        checkExcluded(fileMetadata.getFile().getPath());

        final File driveMetadata = new File();

        driveMetadata.setName(fileMetadata.getFileName());
        driveMetadata.setDescription(fileMetadata.getDescription());
        driveMetadata.setMimeType(fileMetadata.getMimeType());
        driveMetadata.setFileExtension(fileMetadata.getExtension());
        driveMetadata.setVersion(fileMetadata.getVersion());

        uploadWorker(fileMetadata.getFile().getPath(), path, driveMetadata);
    }

    @Override
    public void uploadCollection(final List<String> files, final String path) {
        validateMethod(files, path);
        files.forEach(this::checkExcluded);

        files.forEach(file -> uploadWorker(file, path, null));
    }

    private void uploadWorker(final String filePath, final String path, File fileMetadata) {
        if (service == null)
            initialize();

        final java.io.File file = new java.io.File(filePath);

        if (fileMetadata == null) {
            fileMetadata = new File();
            fileMetadata.setName(file.getName());
        }

        final List<String> parentId = getParentId(path);

        if (!parentId.isEmpty())
            fileMetadata.setParents(parentId);

        final FileContent fileContent = new FileContent(findMimeType(filePath), file);

        try {
            service.files().create(fileMetadata, fileContent).setFields("id").execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void download(final String path) {
        validateMethod(path);

        downloadWorker(path);
    }

    @Override
    public void downloadMultiple(final List<String> filePaths) {
        filePaths.forEach(this::downloadWorker);
    }

    private void downloadWorker(final String path) {
        if (service == null)
            initialize();

        if (!path.contains("/")) {
            findFileByName(path).forEach(file -> downloadFile(file.getId(), path));
            return;
        }

        final String fileName = path.substring(path.lastIndexOf("/"));

        findFileByName(fileName).forEach(file -> downloadFile(file.getId(), fileName));
    }

    private void downloadFile(final String fileId, final String fileName) {
        try {
            final OutputStream outputStream = new FileOutputStream(
                    String.format("%s/Downloads/%s", System.getProperty("user.home"), fileName));

            service.files().get(fileId).executeMediaAndDownloadTo(outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void createDir(final String dirPath) {
        validateMethod(dirPath);

        if (service == null)
            initialize();

        final File fileMetadata = new File();

        // last path component
        final String dirName = dirPath.substring(dirPath.lastIndexOf("/")).substring(1);

        fileMetadata.setName(dirName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");

        final List<String> parentId = getParentId(dirPath);

        if (!parentId.isEmpty())
            fileMetadata.setParents(parentId);

        try {
            service.files().create(fileMetadata).setFields("id").execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private List<String> getParentId(final String path) {
        final String[] dirNames = trimPath(path).split("/");

        final List<String> folderId = new ArrayList<>();

        String pageToken = null;

        for (final String name : dirNames)
            try {
                final FileList result = service.files().list()
                        .setQ(String.format("name='%s'", name))
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id)")
                        .setPageToken(pageToken)
                        .execute();

                final List<File> foundFiles = result.getFiles();

                if (foundFiles.isEmpty())
                    return folderId;

                if (folderId.size() == 0)
                    folderId.add(foundFiles.get(0).getId());
                else
                    folderId.set(0, foundFiles.get(0).getId());

                pageToken = result.getNextPageToken();
            } catch (IOException e) {
                e.printStackTrace();
            }

        return Collections.unmodifiableList(folderId);
    }

    private String trimPath(final String dirPath) {
        if (dirPath.charAt(0) == '/')
            return dirPath.substring(1, dirPath.lastIndexOf("/"));

        return dirPath.substring(0, dirPath.lastIndexOf("/"));
    }

    @Override
    public List<File> findFileByName(final String name) {
        validateMethod(name);

        return findBy(String.format("name='%s'", name));
    }

    @Override
    public List<File> findFileByExtension(final String extension) {
        validateMethod(extension);

        return findBy(String.format("name contains '%s'", extension));
    }

    @Override
    public List<File> findDirectory(final String name) {
        validateMethod(name);

        return findBy(String.format("name='%s' and mimeType='application/vnd.google-apps.folder'", name));
    }

    private List<File> findBy(final String quarry) {
        if (service == null)
            initialize();

        String pageToken = null;

        FileList result = null;

        do {
            try {
                result = service.files().list()
                        .setQ(quarry)
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name)")
                        .setPageToken(pageToken)
                        .execute();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (result == null)
                return Collections.emptyList();

            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        return result.getFiles();
    }
}
