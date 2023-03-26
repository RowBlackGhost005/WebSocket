/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package websockets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import domain.Cancion;
import domain.Message;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

/**
 *
 * @author lv1013
 * Es un ejemplo de websocket donde cada mensaje que llega un cliente
 * es replicado a quienes estén conectados
 */
@ServerEndpoint("/websocketendpoint")
public class WebSockete {
    
    private final String SVR_TOKEN = "svr:123";
    
    Message message;
    
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
	//para guardar la sesión de cada cliente y poder replicar el mensaje a cada uno
	//se hace una colección sincronizada para el manejo de la concurrencia
    private static Set<Session> clients = 
            Collections.synchronizedSet(new HashSet<Session>());    
    
    @OnOpen
    public void onOpen(Session sesion){
        System.out.println("Open Connection ...");
		//al conectarse un cliente se abre el websocket y se guarda su sesión.
        clients.add(sesion);
        
        // Notifica al usuario conectado su ID dentro del chat
        message = new Message(SVR_TOKEN, "add:uid", sesion.getId());
        sendMessageSingleClient(message, sesion);
        
        
        
        // Notifica a TODOS los clientes que se conectó un nuevo usuario
        message = new Message(SVR_TOKEN, "add:usr", sesion.getId());
        sendMessageAllClients(message, sesion);
        
        // Envía al nuevo usuario la lista de usuarios ya conectados (Uno por uno | INTENTAR CAMBIAR)
        sendOnlineClients(sesion);
    }
     
    @OnClose
    public void onClose(Session sesion){
        System.out.println("Close Connection ...");
		//al cerrarse la conexión por parte del cliente se elimina su sesión en el servidor
        clients.remove(sesion);
        
        //Envía un mensaje a todos los usuarios para eliminar a este usuario
        message = new Message(SVR_TOKEN, "del:usr", sesion.getId());
        sendMessageAllClients(message, sesion);
    }
     
    @OnMessage
    public void onMessage(String receivedMessage, Session sesion){
        System.out.println("Message from the client: " + receivedMessage);
        
        JsonObject messageJson = new Gson().fromJson(receivedMessage, JsonObject.class);
        String sendTo = messageJson.get("to").getAsString();
        String messageText = messageJson.get("message").getAsString();
        String messageType = messageJson.get("type").getAsString();
        
        String command = null;
        
        if(messageType.equalsIgnoreCase("msg")){
            command = "add:msg";
        }else if(messageType.equalsIgnoreCase("jsn")){
            command = "add:obj";

            Cancion cancion = gson.fromJson(messageText, Cancion.class);
            messageText = gson.toJson(cancion);
        }
        
        if(sendTo.equalsIgnoreCase("All")){
            message = new Message(sesion.getId() , command , sesion.getId() + ": " + messageText);
            sendMessageAllClients(message, sesion);
        }else{
            message = new Message(sesion.getId() , command , "[Privado] " + sesion.getId() + ": " + messageText);
            sendMessageSingleClient(message, getOnlineClient(sendTo));
        }
        
        //String echoMsg = sesion.getId() + ": " + receivedMessage;
        
        // = new Message(SVR_TOKEN, "add:msg", echoMsg);
        
        //sendMessageAllClients(message, sesion);
    }
 
    @OnError
    public void onError(Throwable e){
        e.printStackTrace();
    }    
    
    private void sendMessageAllClients(Message message, Session sesion) {
        synchronized (clients) {
            // Se itera sobre la sesiones (clientes) guardados para transmitir el mensaje
            for (Session client : clients) {
                if (!client.equals(sesion)) {
                    try {
                        client.getBasicRemote().sendText(gson.toJson(message));
                    } catch (IOException ex) {
                        System.out.println(ex);
                    }
                }
            }
        }
    }
    
    private void sendMessageSingleClient(Message message, Session sesion){
        synchronized (clients) {
            // Se itera sobre la sesiones (clientes) guardados para transmitir el mensaje
            for (Session client : clients) {
                if (client.equals(sesion)) {
                    try {
                        client.getBasicRemote().sendText(gson.toJson(message));
                        break;
                    } catch (IOException ex) {
                        System.out.println(ex);
                    }
                }
            }
        }
    }
    
    private void sendOnlineClients(Session sesion){
        synchronized (clients) {
            // Se itera sobre la sesiones (clientes) guardados para transmitir el mensaje
            for (Session client : clients) {
                if (!client.equals(sesion)) {
                    message = new Message(SVR_TOKEN, "add:usr", client.getId());
                    sendMessageSingleClient(message, sesion);
                }
            }
        }
    }
    
    private Session getOnlineClient(String clientId){
        synchronized (clients) {
            // Se itera sobre la sesiones (clientes) guardados para transmitir el mensaje
            for (Session client : clients) {
                if (client.getId().equalsIgnoreCase(clientId)) {
                    return client;
                }
            }
        }
        
        return null;
    }
}
