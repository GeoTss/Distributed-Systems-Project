package org.ServerSide.JSON_Deserializers;

import com.google.gson.*;
import org.ServerSide.ServerConfigInfo;

import java.lang.reflect.Type;

public class ServerConfigDeserializer implements JsonDeserializer<ServerConfigInfo> {

    @Override
    public ServerConfigInfo deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        ServerConfigInfo configInfo = new ServerConfigInfo(
                obj.get("WorkerChunks").getAsInt(),
                obj.get("NumberOfReplicas").getAsInt()
        );

        return configInfo;
    }
}
