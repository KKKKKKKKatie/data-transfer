import java.io.*;
import java.net.*;

public class sender {
    private static int cur_ack = 0;
    private static int nextseqnum = 0;
    private static boolean is_timeout = false;
    private static long timer;
    private static int emu_udp_sender;
    private static int emu_udp_receiver;
    private static PrintWriter ack_log;
    private static PrintWriter seqnum_log;

    public static void main(String[] args) throws Exception {
        //check/get input
        if (args.length != 4) {
            System.exit(-1);
        }
        String emu_host_add = args[0];
        InetAddress emu_add = InetAddress.getByName(emu_host_add);
        emu_udp_sender = Integer.parseInt(args[1]);
        emu_udp_receiver = Integer.parseInt(args[2]);
        String file = args[3];
        File newfile = new File(file);
        //read data from file
        int file_len = (int)newfile.length();
        byte byte_array[] = new byte[file_len];
        FileInputStream fis = new FileInputStream(newfile);
        for (int i = 0; i < file_len; i++) {
            fis.read(byte_array);
        }
        fis.close();
        //put data into packets
        int pck_len = file_len / 500;
        if (file_len % 500 != 0) pck_len++;
        packet pck[] = new packet[pck_len];
        for (int i = 0; i < pck_len; i++) {
            int data_size = 500;
            if ((i == pck_len - 1) && (file_len % 500 != 0)) data_size = file_len % 500;
            byte data[] = new byte[data_size];
            for (int j = 0; j < data_size; j++) {
                data[j] = byte_array[500 * i + j];
            }
            pck[i] = packet.createPacket(i % 32, new String(data));
        }
        //create ack thread
        DatagramSocket socket = new DatagramSocket(emu_udp_receiver);
        ackthread ack_thread = new ackthread(socket);
        //start a thread by calling its run method
        ack_thread.start();
        //output
        seqnum_log = new PrintWriter("seqnum.log");
        ack_log = new PrintWriter("ack.log");
        while (true) {
            //time out, resend packets
            if (is_timeout && ((timer + 150) < System.currentTimeMillis())) nextseqnum = cur_ack;
            //check windowsize
            if (nextseqnum < cur_ack + 10) {
                //EOF
                if (cur_ack >= pck.length){
                    packet ack_eot = packet.createEOT(nextseqnum);
                    byte[] data = ack_eot.getUDPdata();
                    DatagramPacket packet = new DatagramPacket(data, data.length, emu_add, emu_udp_sender);
                    socket.send(packet);
                    break;
                } else if (nextseqnum < pck.length) {
                    byte[] data = pck[nextseqnum].getUDPdata();
                    DatagramPacket packet = new DatagramPacket(data, data.length, emu_add, emu_udp_sender);
                    socket.send(packet);
                    seqnum_log.println(pck[nextseqnum].getSeqNum());
                    seqnum_log.flush();
                    //set timer
                    if (cur_ack == nextseqnum) {
                        is_timeout = true;
                        timer = System.currentTimeMillis();
                    }
                    nextseqnum++;
                }
            }
        }
    }
    //new thread receive acks
    public static class ackthread implements Runnable {
        private Thread thread;
        private DatagramSocket socket;
        
        public void start() {
            if (thread == null) {
                thread = new Thread(this);
                thread.start();
            }
        }

        public ackthread(DatagramSocket UDPSocket) {
            socket = UDPSocket;
        }
        //entry point for the thread
        public void run() {
            //receive acks
            while (true) {
                byte[] byte_array = new byte[512];
                DatagramPacket udppacket = new DatagramPacket(byte_array, byte_array.length);
                packet rec_pkt = null;
                try {
                    socket.receive(udppacket);
                    rec_pkt = packet.parseUDPdata(byte_array);
                } catch (Exception e) {
                    System.exit(-1);
                }
                int type = rec_pkt.getType();
                //ack
                if (type == 0) {
                    ack_log.println(rec_pkt.getSeqNum());
                    ack_log.flush();
                    //check duplicate ack
                    if (((cur_ack - 1) % 32) == rec_pkt.getSeqNum()) continue;
                    else {
                        while (cur_ack % 32 != rec_pkt.getSeqNum()) cur_ack++;
                        cur_ack++;
                        //ack timer
                        if (cur_ack == nextseqnum) is_timeout = false;
                        else {
                            is_timeout = true;
                            timer = System.currentTimeMillis();
                        }
                    }
                // EOF
                } else if (type == 2) {
                    seqnum_log.close();
                    ack_log.close();
                    break;
                }
            }
        }
        
    }
}

