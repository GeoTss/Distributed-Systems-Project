package org.ServerSide;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.Domain.Shop;
import org.ServerSide.JSON_Deserializers.ServerConfigDeserializer;
import org.ServerSide.JSON_Deserializers.ShopDeserializer;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

public class ServerFileLoader {

    public static final String SHOP_DIRECTORY_DIR_NAME = "JSON-Shop-Files";
    public static final String SERVER_CONFIG_FILENAME = "Server-Config.json";

    private static String load_json_contents(String _fileName) {
        String json = null;
        try {
            FileInputStream inputStream = new FileInputStream(_fileName);
            byte[] buffer = inputStream.readAllBytes();
            inputStream.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return json;
    }

    private static ArrayList<Path> load_shop_directory(Path _file_path) throws IOException {
        ArrayList<Path> shop_files = new ArrayList<>();
        try(DirectoryStream<Path> stream = Files.newDirectoryStream(_file_path)){
            for(Path file: stream){
                if(Files.isRegularFile(file)){
                    shop_files.add(file);
                }
                else if(Files.isDirectory(file)){
                    ArrayList<Path> result =  load_shop_directory(file);
                    shop_files.addAll(result);
                }
            }
        }
        return shop_files;
    }

    public static ServerConfigInfo load_config() throws URISyntaxException {
        URL resource = ServerFileLoader.class.getClassLoader().getResource(SERVER_CONFIG_FILENAME);
        if (resource == null) {
            throw new IllegalArgumentException("Folder not found in resources: " + SERVER_CONFIG_FILENAME);
        }
        System.out.println("resource = " + resource);

        Path config_filepath = Paths.get(resource.toURI());

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ServerConfigInfo.class, new ServerConfigDeserializer())
                .create();

        String json = load_json_contents(config_filepath.toString());
        ServerConfigInfo config = gson.fromJson(json, ServerConfigInfo.class);

        return config;
    }

    public static ArrayList<Shop> load_shops() throws IOException, URISyntaxException {
        ArrayList<Shop> database_shops = new ArrayList<>();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Shop.class, new ShopDeserializer())
                .create();

        String folderNameInResources = SHOP_DIRECTORY_DIR_NAME;
        URL resource = ServerFileLoader.class.getClassLoader().getResource(folderNameInResources);
        if (resource == null) {
            throw new IllegalArgumentException("Folder not found in resources: " + folderNameInResources);
        }
        System.out.println("resource = " + resource);

        Path directory = Paths.get(resource.toURI());
        ArrayList<Path> files = load_shop_directory(directory);

        for (Path file : files) {
            String json = load_json_contents(file.toString());
            Shop shop = gson.fromJson(json, Shop.class);
            database_shops.add(shop);
        }
        return database_shops;
    }

    public static void main(String[] args) throws IOException, URISyntaxException {

        System.out.println("Loading shops...");
        ArrayList<Shop> shops = ServerFileLoader.load_shops();

        System.out.println("Shops loaded:");
        for (Shop shop : shops) {
            System.out.println(shop + "\n");
        }

        ServerConfigInfo config = ServerFileLoader.load_config();

        System.out.println("WorkerChunks = " + config.getWorker_chunk());
    }
}
