package com.pllapallpal;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static javax.swing.JOptionPane.showMessageDialog;

public class SelectorThread implements Runnable {

    private final Selector selector;
    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();

    private static Consumer<List<Auction>> onReceiveAuctionList;
    private static Consumer<List<String>> onReceiveUserList;
    private static Consumer<Auction> onEnterAuction;
    private static Consumer<Auction> onQuitAuction;
    private static Consumer<String> onMessageReceived;
    private static Consumer<Integer> onUpdateLeftTime;

    public SelectorThread(Selector selector) {
        this.selector = selector;
    }

    @Override
    public void run() {
        try {
            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keys = selectedKeys.iterator();
                while (keys.hasNext()) {
                    SelectionKey selectionKey = keys.next();
                    if (selectionKey.isReadable()) {
                        ByteBuffer byteBuffer = read(selectionKey);
                        // DEBUG
                        System.out.println(byteBuffer.toString());
                        int protocol = byteBuffer.getInt();
                        switch (protocol) {
                            case Protocol.LOGIN: {
                                break;
                            }
                            case Protocol.LOGOUT: {
                                return;
                            }
                            case Protocol.LIST_AUCTION: {
                                int numData = byteBuffer.getInt();
                                List<Auction> auctionList = Data.getInstance().getAuctionList();
                                auctionList.clear();
                                try {
                                    for (int i = 0; i < numData; ++i) {

                                        int keyBytes = byteBuffer.getInt();
                                        byte[] byteKey = new byte[keyBytes];
                                        byteBuffer.get(byteKey, byteBuffer.arrayOffset(), keyBytes);
                                        String key = new String(byteKey, StandardCharsets.UTF_8);

                                        int creatorNameBytes = byteBuffer.getInt();
                                        byte[] byteCreatorName = new byte[creatorNameBytes];
                                        byteBuffer.get(byteCreatorName, byteBuffer.arrayOffset(), creatorNameBytes);
                                        String creatorName = decoder.decode(ByteBuffer.wrap(byteCreatorName)).toString();

                                        int imgBytes = byteBuffer.getInt();
                                        byte[] byteImg = new byte[imgBytes];
                                        byteBuffer.get(byteImg, byteBuffer.arrayOffset(), imgBytes);
                                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteImg);
                                        BufferedImage itemImage = ImageIO.read(byteArrayInputStream);
                                        byteArrayInputStream.close();

                                        int itemNameBytes = byteBuffer.getInt();
                                        byte[] byteName = new byte[itemNameBytes];
                                        byteBuffer.get(byteName, byteBuffer.arrayOffset(), itemNameBytes);
                                        String itemName = decoder.decode(ByteBuffer.wrap(byteName)).toString();

                                        int startingPrice = byteBuffer.getInt();

                                        Auction item = new Auction(key);
                                        item.setCreatorName(creatorName);
                                        item.setItemImage(itemImage);
                                        item.setItemName(itemName);
                                        item.setStartingPrice(startingPrice);
                                        auctionList.add(item);
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                onReceiveAuctionList.accept(auctionList);
                                break;
                            }
                            case Protocol.LIST_USER: {
                                List<String> userList = Data.getInstance().getUserList();
                                userList.clear();
                                int numData = byteBuffer.getInt();
                                for (int i = 0; i < numData; ++i) {
                                    int usernameBytesLength = byteBuffer.getInt();
                                    byte[] usernameBytes = new byte[usernameBytesLength];
                                    byteBuffer.get(usernameBytes, byteBuffer.arrayOffset(), usernameBytesLength);
                                    userList.add(decoder.decode(ByteBuffer.wrap(usernameBytes)).toString());
                                }
                                byteBuffer.clear();
                                onReceiveUserList.accept(userList);
                                break;
                            }
                            case Protocol.AUCTION_ENTER: {
                                byte isUserInAuction = byteBuffer.get();
                                int keyBytes = byteBuffer.getInt();
                                byte[] byteKey = new byte[keyBytes];
                                byteBuffer.get(byteKey, byteBuffer.arrayOffset(), keyBytes);
                                String auctionKey = new String(byteKey, StandardCharsets.UTF_8);
                                if (isUserInAuction == Protocol.FALSE) {
                                    showMessageDialog(null, "You are already in auction.");
                                } else if (isUserInAuction == Protocol.TRUE) {
                                    for (Auction auction : Data.getInstance().getAuctionList()) {
                                        if (auctionKey.equals(auction.getKey())) {
                                            onEnterAuction.accept(auction);
                                        }
                                    }
                                }
                                break;
                            }
                            case Protocol.AUCTION_MESSAGE: {
                                int messageBytes = byteBuffer.getInt();
                                byte[] byteMessage = new byte[messageBytes];
                                byteBuffer.get(byteMessage, byteBuffer.arrayOffset(), messageBytes);
                                String message = new String(byteMessage, StandardCharsets.UTF_8);
                                onMessageReceived.accept(message);
                                break;
                            }
                            case Protocol.AUCTION_QUIT: {
                                onQuitAuction.accept(Data.getInstance().getCurrentAuction());
                                break;
                            }
                            case Protocol.AUCTION_LEFT_TIME: {
                                int leftTime = byteBuffer.getInt();
                                onUpdateLeftTime.accept(leftTime);
                            }
                        }
                    }
                    keys.remove();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addOnReceiveAuctionList(Consumer<List<Auction>> onReceiveAuctionList) {
        SelectorThread.onReceiveAuctionList = onReceiveAuctionList;
    }

    public static void addOnReceiveUserList(Consumer<List<String>> onReceiveUserList) {
        SelectorThread.onReceiveUserList = onReceiveUserList;
    }

    public static void addOnEnterAuction(Consumer<Auction> onEnterAuction) {
        SelectorThread.onEnterAuction = onEnterAuction;
    }

    public static void addOnQuitAuction(Consumer<Auction> onQuitAuction) {
        SelectorThread.onQuitAuction = onQuitAuction;
    }

    public static void addOnMessageReceived(Consumer<String> onMessageReceived) {
        SelectorThread.onMessageReceived = onMessageReceived;
    }

    public static void addOnUpdateLeftTime(Consumer<Integer> onUpdateLeftTime) {
        SelectorThread.onUpdateLeftTime = onUpdateLeftTime;
    }

    private ByteBuffer read(SelectionKey selectionKey) throws IOException {

        SocketChannel socketChannel = (SocketChannel) selectionKey.channel();

        ByteBuffer capacityBuffer = ByteBuffer.allocate(Integer.BYTES);
        socketChannel.read(capacityBuffer);
        capacityBuffer.flip();
        int capacity = capacityBuffer.getInt();

        ByteBuffer byteBuffer = ByteBuffer.allocate(capacity);
        while (byteBuffer.hasRemaining()) {
            synchronized (socketChannel) {
                if (socketChannel.isConnected()) {
                    socketChannel.read(byteBuffer);
                }
            }
        }
        byteBuffer.flip();

        return byteBuffer;
    }
}
