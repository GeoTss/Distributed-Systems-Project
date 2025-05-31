package com.example.client_efood.ServerSide.JSON_Deserializers;

import com.google.gson.*;
import com.example.client_efood.ServerSide.ServerConfigInfo;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class ServerConfigDeserializer implements JsonDeserializer<ServerConfigInfo> {

    @Override
    public ServerConfigInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        ServerConfigInfo configInfo = new ServerConfigInfo(
                obj.get("WorkerCount").getAsInt(),
                obj.get("NumberOfReplicas").getAsInt(),
                obj.get("ClientBatchStreamSize").getAsInt()
        );

        ArrayList<Integer> worker_ports = new ArrayList<>();

        JsonArray worker_info_arr = obj.getAsJsonArray("WorkerInfo");
        for(JsonElement worker: worker_info_arr){
            JsonObject portObj = worker.getAsJsonObject();
            int port = portObj.get("Port").getAsInt();
            worker_ports.add(port);
        }

        configInfo.setWorker_ports(worker_ports);

        return configInfo;
    }
}
