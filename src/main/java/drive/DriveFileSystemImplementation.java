package drive;

import meta.FileMetaData;
import system.FileSystem;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DriveFileSystemImplementation implements FileSystem {

    public DriveFileSystemImplementation() {

    }

    @Override
    public void initialize() {

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
