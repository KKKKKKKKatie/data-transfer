import java.io.*;
import java.net.*;

public class receiver {
    private static int exp_seq_num = 0;
    public static void main(String[] args) throws Exception {
        //check/get input
        if (args.length != 4) {
            System.exit(-1);
        }
        String emu_host_add = args[0];
        InetAddress emu_add = InetAddress.getByName(emu_host_add);
        int emu_udp_ack = Integer.parseInt(args[1]);
        int emu_udp_data = Integer.parseInt(args[2]);
        DatagramSocket rec_data = new DatagramSocket(emu_udp_data);
        String file = args[3];
        PrintWriter writefile = new PrintWriter(file);
        //output
        PrintWriter arrival = new PrintWriter("arrival.log");
        DatagramPacket data_pack;
        //receive data
        while (true) {
            //read data
            byte[] byte_data = new byte[512];
            data_pack = new DatagramPacket(byte_data, byte_data.length);
            rec_data.receive(data_pack);
            packet rec_pack = packet.parseUDPdata(data_pack.getData());
            int rec_type = rec_pack.getType();
            if (rec_type == 1) {
                //write seq number to log file
                arrival.println(rec_pack.getSeqNum());
                arrival.flush();
                //if the sequence number is the one that it is expecting
                if (rec_pack.getSeqNum() == exp_seq_num % 32) {
                    //send an ACK packet back
                    packet ack_back = packet.createACK(rec_pack.getSeqNum());
                    byte[] data = ack_back.getUDPdata();
                    DatagramPacket packet = new DatagramPacket(data, data.length, emu_add, emu_udp_ack);
                    rec_data.send(packet);
                    //write data to file
                    writefile.print(new String(rec_pack.getData()));
                    writefile.flush();
                    exp_seq_num++;
                }
            //EOT
            } else if (rec_type == 2) {
                //send EOT ack
                packet ack_eot = packet.createEOT(rec_pack.getSeqNum());
                byte[] data = ack_eot.getUDPdata();
                DatagramPacket packet = new DatagramPacket(data, data.length, emu_add, emu_udp_ack);
                rec_data.send(packet);
                arrival.close();
                writefile.close();
                //rec_data.close();
            //in all other cases, discard the received packet
            } else {
                //resends an ACK packet for the most recently received in-order packet
                packet ack_back = packet.createACK(rec_pack.getSeqNum() - 1);
                byte[] data = ack_back.getUDPdata();
                DatagramPacket packet = new DatagramPacket(data, data.length, emu_add, emu_udp_ack);
                rec_data.send(packet);
            }
        }
    }
}

