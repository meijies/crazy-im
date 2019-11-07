package com.meijie;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

import static com.meijie.proto.ClientRequestProtos.*;
import static com.meijie.proto.ImRequestProtos.ImResponse;


/**
 * 一个简单的命令行客户端
 *
 * @author meijie
 */
public class CmdClient {

    private static final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    private static final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(System.out));
    private static final BufferedWriter error = new BufferedWriter(new OutputStreamWriter(System.err));
    private static final ImClient imClient = new ImClient();

    private static final Map<Long, String> fileIndexPathMap = new HashMap<>();

    private static final Runnable receiveRunnable = () -> {
        try {
            while (true) {
                ImResponse response = imClient.takeMessageAndWait(1);
                if (response != null) {
                    switch (response.getMethodName()) {
                        case "getMessage":
                            dealGetMessageResponse(response);
                            break;
                        case "sendMessage":
                            dealSendMessageResponse(response);
                            break;
                        case "getFile":
                            dealGetFileResponse(response);
                            break;
                        case "sendFile":
                            dealSendFileResponse(response);
                            break;
                        default:
                            dealUnknownResponse(response);
                    }
                }
            }
        } catch (InterruptedException | IOException e) {
            try {
                printErrorMessage("获取消息的线程异常中断，退出程序");
                System.exit(-1);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    };

    private static void dealUnknownResponse(ImResponse response) throws IOException {
        printErrorMessage("未知的[" + response.getMethodName() + "]请求相应");
    }

    private static void dealSendFileResponse(ImResponse response) throws IOException {
        FileResponseAckProto fileResponseAckProto = response.getPlayload().unpack(FileResponseAckProto.class);

        printMessage("文件发送成功[消息标识: " + fileResponseAckProto.getMessageIdentify() +
                ", 文件标识: " + fileResponseAckProto.getFileId() + "]");
    }

    private static void dealGetFileResponse(ImResponse response) throws IOException {
        ImFileProto imFileProto = response.getPlayload().unpack(ImFileProto.class);
        File file = new File(fileIndexPathMap.get(imFileProto.getFileId()));
        if (file.exists()) {
            printErrorMessage("[文件标识: " + imFileProto.getFileId() +
                    "] 要存储到的文件[" + file.getAbsolutePath() + "]已存在于磁盘");
        } else {
            file.createNewFile();
        }
        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file))) {
            imFileProto.getFile().writeTo(outputStream);
            printMessage("接收文件成功[文件标识: " + imFileProto.getFileId() + ", 存储路径: " + file.getAbsolutePath() + "]");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void dealSendMessageResponse(ImResponse response) throws IOException {
        MessageResponseAckProto messageResponseAckProto = response.getPlayload().unpack(MessageResponseAckProto.class);
        printMessage("消息发送成功[消息标识: " + messageResponseAckProto.getMessageIdentify() + "]");
    }

    private static void dealGetMessageResponse(ImResponse response) throws IOException {
        ImMessageListProto imMessageListProto = response.getPlayload().unpack(ImMessageListProto.class);
        for (ImMessageProto imMessageProto : imMessageListProto.getImMessageListList()) {
            printMessage("接收消息[发送人: " + imMessageProto.getSender()
                    + ", 消息内容:" + imMessageProto.getContent()
                    + ", 消息版本:" + imMessageProto.getVersion() + "]");
        }
    }

    // 单线程，无并发冲突
    private static long messageIdentify = 0;

    public static void main(String[] args) throws IOException {
        long userId = 0;
        if (args.length > 0) {
            userId = Long.parseLong(args[0]);
        } else {
            error.newLine();
            error.write("请输入用户ID");
            error.flush();
        }

        Thread receiverThread = new Thread(receiveRunnable);
        receiverThread.setDaemon(true);
        receiverThread.start();

        while (true) {
            String userCmd = reader.readLine();
            String[] paramArray = userCmd.split("\\s+");
            if (paramArray.length > 1) {
                if (StringUtils.equalsIgnoreCase("getMessage", paramArray[0])) {
                    getMessage(userId, paramArray);
                } else if (StringUtils.equalsIgnoreCase("sendMessage", paramArray[0])) {
                    sendMessage(userId, paramArray);
                } else if (StringUtils.equalsIgnoreCase("getFile", paramArray[0])) {
                    getFile(paramArray);
                } else if (StringUtils.equalsIgnoreCase("sendFile", paramArray[0])) {
                    sendFile(paramArray);
                } else {
                    printUnknownCmd();
                }
            }
        }

    }

    private static void getFile(String[] paramArray) throws IOException {
        if (paramArray.length > 2 &&
                StringUtils.isNumeric(paramArray[1])) {
            File file = new File(paramArray[2]);
            if (file.exists()) {
                printErrorMessage("文件[" + paramArray[2] + "]已经存在，无法接收新的文件");
            }
            fileIndexPathMap.put(Long.parseLong(paramArray[1]), paramArray[2]);
            imClient.getFile(Long.parseLong(paramArray[1]));
        } else {
            printErrorParameter();
        }
    }

    private static void sendFile(String[] paramArray) throws IOException {
        messageIdentify = messageIdentify + 1;
        if (paramArray.length > 1) {
            File file = new File(paramArray[1]);
            if (!file.exists()) {
                printErrorMessage("文件: [" + paramArray[1] + "] 不存在");
            }
            imClient.sendFile(new BufferedInputStream(new FileInputStream(file)), messageIdentify);
        } else {
            printErrorParameter();
        }
    }

    private static void sendMessage(long userId, String[] paramArray) throws IOException {
        if (paramArray.length > 2 &&
                StringUtils.isNumeric(paramArray[1])) {
            messageIdentify = messageIdentify + 1;
            imClient.sendMessage(userId, Long.parseLong(paramArray[1]), paramArray[2], messageIdentify);
        } else {
            printErrorParameter();
        }
    }

    private static void getMessage(long userId, String[] paramArray) throws IOException {
        if (paramArray.length > 2 &&
                StringUtils.isNumeric(paramArray[1]) &&
                StringUtils.isNumeric(paramArray[2])) {
            imClient.getMessage(userId, Long.parseLong(paramArray[1]), Long.parseLong(paramArray[2]));
        } else {
            printErrorParameter();
        }
    }

    private static final void printMessage(String message) throws IOException {
        writer.newLine();
        writer.write(message);
        writer.newLine();
        writer.flush();
    }

    private static final void printUnknownCmd() throws IOException {
        printErrorMessage("未知的命令");
    }

    private static final void printErrorParameter() throws IOException {
        printErrorMessage("参数错误");
    }

    private static final void printErrorMessage(String message) throws IOException {
        error.newLine();
        error.write(message);
        error.flush();
    }
}
