package com.obana.heleway;

/*
 * Fwd: the port forwarding app
 * Copyright (C) 2016  Elixsr Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.Callable;

import android.util.Log;

//import com.elixsr.portforwarder.exceptions.BindException;

/**
 * Created by Niall McShane on 21/02/2016.
 * <p>
 * Credit: https://alexapps.net/single-threaded-port-forwarding-utility-/
 */
public class TcpForwarder extends Forwarder implements Callable<Void> {

    private static final String TAG = "TcpForwarder";
    private static final int BUFFER_SIZE = 800000;
    InetSocketAddress from;
    InetSocketAddress to;
    public TcpForwarder(InetSocketAddress from, InetSocketAddress to, String ruleName) {
        super("TCP", from, to, ruleName);
        this.from = from;
        this.to = to;
    }

    public Void call() throws IOException {

        new Thread(mainRunner).start();

        return null;
    }
    
    Runnable mainRunner = new Runnable() {
   	 @Override // java.lang.Runnable
        public void run() {
   		LogUtils.i("TcpForwarder starting....prot:" + protocol + " from:" + from.getPort() + " to:" +to.getPort());

        try {
            Selector selector = Selector.open();

            ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);

            ServerSocketChannel listening = ServerSocketChannel.open();
            listening.configureBlocking(false);

            try {
                listening.socket().bind(from, 0);
            } catch (java.net.BindException e) {
            	LogUtils.e("TcpForwarder BindException" + e.getMessage());
                throw new IOException("TcpForwarder BindException" + e.getMessage());
            } catch (java.net.SocketException e) {
            	LogUtils.e("TcpForwarder SocketException" + e.getMessage());
                throw new IOException("TcpForwarder SocketException" + e.getMessage());
            }

            listening.register(selector, SelectionKey.OP_ACCEPT, listening);

            while (true) {

                if (Thread.currentThread().isInterrupted()) {
                	LogUtils.e("TcpForwarder isInterrupted");
                    listening.close();
                    break;
                }

                int count = selector.select();
                
                if (count > 0) {
                    Iterator<SelectionKey> it = selector.selectedKeys().iterator();
                    while (it.hasNext()) {

                        SelectionKey key = it.next();
                        it.remove();
                        //LogUtils.i("tcpforward run, key:" + key.toString());
                        
                        if (key.isValid() && key.isAcceptable()) {
                        	LogUtils.e("TcpForwarder processAcceptable, to:" + to.getHostString());
                            processAcceptable(key, to);
                        }

                        if (key.isValid() && key.isConnectable()) {
                        	LogUtils.e("TcpForwarder processConnectable, key:" + key.toString());
                            processConnectable(key);
                        }

                        if (key.isValid() && key.isReadable()) {
                            processReadable(key, readBuffer);
                        }

                        if (key.isValid() && key.isWritable()) {
                            processWritable(key);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LogUtils.e("TcpForwarder Problem opening Selector " + e.getMessage());
        }
   	 }
   	
   };
   

    private static void registerReads(
            Selector selector,
            SocketChannel socket,
            SocketChannel forwardToSocket) throws ClosedChannelException {
        RoutingPair pairFromToPair = new RoutingPair();
        pairFromToPair.from = socket;
        pairFromToPair.to = forwardToSocket;
        pairFromToPair.from.register(selector, SelectionKey.OP_READ, pairFromToPair);

        RoutingPair pairToFromPair = new RoutingPair();
        pairToFromPair.from = forwardToSocket;
        pairToFromPair.to = socket;
        pairToFromPair.from.register(selector, SelectionKey.OP_READ, pairToFromPair);
    }

    private static void processWritable(
            SelectionKey key) throws IOException {

        RoutingPair pair = (RoutingPair) key.attachment();

        pair.writeBuffer.flip();
        pair.to.write(pair.writeBuffer);

        if (pair.writeBuffer.remaining() > 0) {
            pair.writeBuffer.compact();
        } else {
            key.interestOps(SelectionKey.OP_READ);
            pair.writeBuffer.clear();
        }
    }

    private static void processReadable(
            SelectionKey key,
            ByteBuffer readBuffer) throws IOException {

        readBuffer.clear();
        RoutingPair pair = (RoutingPair) key.attachment();

        int r = 0;
        try {
            r = pair.from.read(readBuffer);
        } catch (IOException e) {
            key.cancel();
            LogUtils.e("TcpForwarder processReadable Connection closed, e: " + e.getMessage());
        }
        if (r <= 0) {
            pair.from.close();
            pair.to.close();
            key.cancel();
            LogUtils.e("TcpForwarder processReadable r: " + r);
            LogUtils.e("TcpForwarder processReadable read size error, all Connection closed: " + key.channel());
        } else {
            readBuffer.flip();
            pair.to.write(readBuffer);

            if (readBuffer.remaining() > 0) {
                pair.writeBuffer.put(readBuffer);
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }
    }

    private static void processConnectable(
            SelectionKey key) throws IOException {
        SocketChannel from = (SocketChannel) key.attachment();
        SocketChannel forwardToSocket = (SocketChannel) key.channel();

        forwardToSocket.finishConnect();
        forwardToSocket.socket().setTcpNoDelay(true);
        registerReads(key.selector(), from, forwardToSocket);
    }

    private static void processAcceptable(
            SelectionKey key,
            InetSocketAddress forwardToAddress) throws IOException {
        SocketChannel from = ((ServerSocketChannel) key.attachment()).accept();
        System.out.println("Accepted " + from.socket());
        from.socket().setTcpNoDelay(true);
        from.configureBlocking(false);

        SocketChannel forwardToSocket = SocketChannel.open();
        forwardToSocket.configureBlocking(false);

        boolean connected = forwardToSocket.connect(forwardToAddress);
        if (connected) {
            forwardToSocket.socket().setTcpNoDelay(true);
            registerReads(key.selector(), from, forwardToSocket);
        } else {
            forwardToSocket.register(key.selector(), SelectionKey.OP_CONNECT, from);
        }
    }

    static class RoutingPair {
        SocketChannel from;
        SocketChannel to;
        ByteBuffer writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    }
}
