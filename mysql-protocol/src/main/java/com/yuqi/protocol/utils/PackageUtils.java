package com.yuqi.protocol.utils;

import com.google.common.collect.Lists;
import com.yuqi.protocol.io.ReaderAndWriter;
import com.yuqi.protocol.pkg.MySQLPackage;
import com.yuqi.protocol.pkg.ResultSetHolder;
import com.yuqi.protocol.pkg.auth.ServerGreeting;
import com.yuqi.protocol.pkg.response.ColumnCount;
import com.yuqi.protocol.pkg.response.ColumnType;
import com.yuqi.protocol.pkg.response.ColumnValue;
import com.yuqi.protocol.pkg.response.Eof;
import com.yuqi.protocol.pkg.response.Ok;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import java.util.List;

import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_CONNECT_WITH_DB;
import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_FOUND_ROWS;
import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_IGNORE_SIGPIPE;
import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_IGNORE_SPACE;
import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_INTERACTIVE;
import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_LONG_FLAG;
import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_LONG_PASSWORD;
import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_ODBC;
import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_PROTOCOL_41;
import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_SECURE_CONNECTION;
import static com.yuqi.protocol.constants.ServerCapabilityFlags.CLIENT_TRANSACTIONS;

/**
 * @author yuqi
 * @mail yuqi5@xiaomi.com
 * @description your description
 * @time 2/7/20 20:44
 **/
public class PackageUtils {

    public static byte[] salt1 = {1, 1, 1, 1, 1, 1, 1, 1};
    public static byte[] salt2 = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    public static ServerGreeting buildInitAuthencatinPackage() {

        int serverCapability = getServerCapality();
        ServerGreeting greetingPackage = ServerGreeting.builder()
                .serverThreadId((int) Thread.currentThread().getId())
                .saltOne(salt1)
                .protocalVeriosn((byte) 10)
                .serverVeriosnInfo("5.7.22")
                //origin is 0xff, cause only disable-ssl mysql -h127.0.0.1 -P3016 -uroot -p123456 --ssl-mode=disabled can connect
                .serverCapability((short) (serverCapability & 0x0000ffff))
                //编码方式, 后面主键完善
                .serverLanguage((byte) 33)
                .serverStatus((short) 2)
                .extendServerCapabilities((short) ((serverCapability >> 16) & 0x0000ffff))
                .authencationPluginLenth((byte) 0x15)
                .saltTwo(salt2)
                .authencationPlugin("mysql_native_password")
                .build();

        return greetingPackage;
    }

    private static int getServerCapality() {
        int flags = 0;

        flags |= CLIENT_LONG_PASSWORD;
        flags |= CLIENT_FOUND_ROWS;
        flags |= CLIENT_LONG_FLAG;
        flags |= CLIENT_CONNECT_WITH_DB;
        flags |= CLIENT_ODBC;
        flags |= CLIENT_IGNORE_SPACE;
        flags |= CLIENT_PROTOCOL_41;
        flags |= CLIENT_INTERACTIVE;
        flags |= CLIENT_IGNORE_SIGPIPE;
        flags |= CLIENT_TRANSACTIONS;
        flags |= CLIENT_SECURE_CONNECTION;
        return flags;
    }


    public static MySQLPackage buildOkMySqlPackage(int affectedRows, int seqNumber, int lastInsertId) {
        Ok okPackage = Ok.builder()
                .header((byte) 0x00)
                .serverStatus(0x0002)
                .affectedRows(affectedRows)
                .lastInsertId(lastInsertId)
                .build();

        MySQLPackage mysqlPacakge = new MySQLPackage(okPackage);
        mysqlPacakge.setSeqNumber((byte) seqNumber);

        return mysqlPacakge;
    }

    public static ByteBuf packageToBuf(ReaderAndWriter readerAndWriter) {
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.buffer(128);
        readerAndWriter.write(byteBuf);

        return byteBuf;
    }

    public static ByteBuf packageToBuf(ReaderAndWriter readerAndWriter, ByteBuf byteBuf) {
        readerAndWriter.write(byteBuf);
        return byteBuf;
    }

    /**
     * 构造ResultSet包
     * @param resultSetHolder
     * @return
     */
    public static ByteBuf buildResultSet(ResultSetHolder resultSetHolder) {
        final List<MySQLPackage> result = Lists.newArrayList();

        final List<List<String>> data = resultSetHolder.getData();
        final int[] columnType = resultSetHolder.getColumnType();
        final String[] columnName = resultSetHolder.getColumnName();

        final int columnNum = columnType.length;

        //query seqNumber is 0 so result query number from 1
        byte seqNumber = 1;

        //first is the column count
        final MySQLPackage columnCountPackage = new MySQLPackage(
                new ColumnCount(columnNum));
        columnCountPackage.setSeqNumber(seqNumber++);
        result.add(columnCountPackage);

        //second is the column detail
        List<MySQLPackage> columnDetails = Lists.newArrayList();

        for (int i = 0; i < columnNum; i++) {
            final MySQLPackage columnTypeMySqlPackage = new MySQLPackage();
            final ColumnType columnTypePackage =
                    ColumnType.builder()
                            .catalog("def")
                            .schema(resultSetHolder.getSchema())
                            .table(resultSetHolder.getTable())
                            .orgTable("")
                            .name(columnName[i])
                            .originalName("")
                            //original 33
                            .charSet(0)
                            .filler((byte) 0x0c)
                            //original 84
                            .columnLength(0)
                            .columnType((byte) columnType[i])
                            .flags(0x00)
                            .dicimals((byte) 0x00)
                            .build();

            columnTypeMySqlPackage.setAbstractReaderAndWriterPackage(columnTypePackage);
            columnTypeMySqlPackage.setSeqNumber(seqNumber++);
            columnDetails.add(columnTypeMySqlPackage);
        }
        result.addAll(columnDetails);

        //third is end of package
        final MySQLPackage eofPackage = new MySQLPackage(new Eof((byte) 0xfe, 0, 0x0002));
        eofPackage.setSeqNumber(seqNumber++);
        result.add(eofPackage);

        //fourth is row value
        List<MySQLPackage> rows = Lists.newArrayList();
        for (List<String> row : data) {
            MySQLPackage columnValue = new MySQLPackage(new ColumnValue(row));
            columnValue.setSeqNumber(seqNumber++);
            rows.add(columnValue);
        }
        result.addAll(rows);

        //eof package again
        final MySQLPackage eofPackageLast = new MySQLPackage(new Eof((byte) 0xfe, 0, 0x0002));
        eofPackageLast.setSeqNumber(seqNumber++);
        result.add(eofPackageLast);


        //write to buffer
        ByteBuf buf = PooledByteBufAllocator.DEFAULT.buffer(128);
        result.forEach(a -> a.write(buf));

        return buf;
    }
}
