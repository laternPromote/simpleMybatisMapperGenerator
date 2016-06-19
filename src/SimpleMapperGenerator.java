import com.google.common.base.CaseFormat;
import javafx.util.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;

/**
 * description:根据传入的数据库配置,生成 实体类 dao daoImpl mapper
 * 用Map<表名, List<Pair<列名,数据类型>>>保存数据库中的表数据
 * 生成文件在 target/generated-sources/simpleMapperGenerator下面
 *
 * generate entity, dao, daoImpl, mapper based on the datasource from generateConfig.properties
 * generated files will be located at /target/generated-sources/simpleMapperGenerator
 *
 * @author geyan
 * @date 2016/6/6
 * @time 11:18
 */
public class SimpleMapperGenerator {

    List<String> tableList = new ArrayList<>();
    private String dbName;
    private String driver;
    private String url;
    private String userName;
    private String passWord;
    private String targetPackage;
    private String sqlSession;


    public SimpleMapperGenerator() {
        parseDataSource();
    }


    /**
     * 根据配置文件获得数据库设置
     * 配置文件名为generateConfig.properties
     * 放在工程的根目录下
     */
    private void parseDataSource() {

        Properties properties = new Properties();
        FileInputStream inputStream = null;

        try {
            inputStream = new FileInputStream("generateConfig.properties");
            properties.load(inputStream);
            this.dbName = properties.getProperty("dbName");
            this.userName = properties.getProperty("userName");
            this.url = properties.getProperty("url");
            this.passWord = properties.getProperty("password");
            this.driver = properties.getProperty("driver");
            this.targetPackage = properties.getProperty("targetPackage");
            this.sqlSession = properties.getProperty("sqlSession");
        } catch (IOException e) {
            System.out.println("读取配置文件失败");
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 从数据库中获取表名,和所有列的名字,数据类型
     *
     * @return
     */
    private Map<String, List<Pair<String, String>>> getColumns() {
        Map<String, List<Pair<String, String>>> columnMapResult = new HashMap<>();
        Connection connection;
//        String tableName=null;
        //get all tables from database
        String getTableSql = "select TABLE_NAME from information_schema.tables where TABLE_SCHEMA=" + "'" + dbName + "'";


        //if the jdbc driver exists
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        try {
            connection = DriverManager.getConnection(url, userName, passWord);
            PreparedStatement pGetTableStatement = connection.prepareStatement(getTableSql);
            ResultSet tableResultSet = pGetTableStatement.executeQuery();

            while (tableResultSet.next()) {
                //getString中的参数1指的是从第一列中取值
                tableList.add(tableResultSet.getString(1));
            }

//            System.out.println(tableList);

            for (String tableName : tableList) {
                String getColumnSql = "SELECT COLUMN_NAME, DATA_TYPE from information_schema.COLUMNS WHERE TABLE_SCHEMA = " + "'" + dbName + "'" + "AND TABLE_NAME =" + "'" + tableName + "'";
                PreparedStatement pGetColumnStatement = connection.prepareStatement(getColumnSql);
                ResultSet columnResultSet = pGetColumnStatement.executeQuery();

                List<Pair<String, String>> columns = new ArrayList<>();
                while (columnResultSet.next()) {
                    Pair<String, String> columnPair = new Pair<String, String>(columnResultSet.getString(1), columnResultSet.getString(2));
                    columns.add(columnPair);

                }
                columnMapResult.put(tableName, columns);
            }

//            printMap(columnMapResult);
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return columnMapResult;
    }

    /**
     * 根据获取的表,生成实体类
     *
     * @param map
     * @return
     */
    private void generateEntity(Map<String, List<Pair<String, String>>> map) {
        Iterator<Map.Entry<String, List<Pair<String, String>>>> hashMapIterator = map.entrySet().iterator();
        while (hashMapIterator.hasNext()) {
            List<String> lines = new ArrayList<>();
            lines.add("import java.util.Date;\r\n");
            Map.Entry<String, List<Pair<String, String>>> entry = hashMapIterator.next();
            lines.add("public class " + entry.getKey() + " {\r\n");
            String fileName = entry.getKey() + ".java";
            Path filePath = Paths.get(fileName);
            //TODO:缺少缩进样式

            //根据列名,生成属性
            for (Pair<String, String> pair : entry.getValue()) {
                String dataType = pair.getValue();
                String columnName = pair.getKey();
                lines.add("private" + " " + dataType + " " + columnName + ";" + "\r\n");
            }
            //生成getter setter
            for (Pair<String, String> pair : entry.getValue()) {
                String dataType = pair.getValue();
                String columnName = pair.getKey();
                lines.add("public" + " " + dataType + " get" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, columnName) + "() {");
                lines.add("return " + columnName + ";");
                lines.add("}\r\n");
                lines.add("public void set" + CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, columnName) + "(" + dataType + " " + columnName + ") {");
                lines.add("this." + columnName + " = " + columnName + ";");
                lines.add("}\r\n");
            }
            lines.add("}\r\n");

            writeToFile("entities", fileName, lines);
        }

    }

    /**
     * 根据获得的表,生成对应的mybatis的mapper
     *
     * @param map
     */
    private void generateMapper(Map<String, List<Pair<String, String>>> map) {
        Iterator<Map.Entry<String, List<Pair<String, String>>>> hashMapIterator = map.entrySet().iterator();


        while (hashMapIterator.hasNext()) {

            List<String> lines = new ArrayList<>();
            Map.Entry<String, List<Pair<String, String>>> entry = hashMapIterator.next();
            String primaryKey = getPrimaryKey(entry);
            String className = entry.getKey();
            String classNameUpperCase = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, className);
            String tableName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, className);
            String fileName = classNameUpperCase + "Mapper" + ".xml";
            lines.add("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
            lines.add("<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\r\n");
            lines.add("<mapper namespace=\"I" + classNameUpperCase + "Dao\">");

            //select
            lines.add("<select id=\"query" + classNameUpperCase + "\" resultType=\"" + targetPackage + "." + classNameUpperCase + "\"");
            lines.add("parameterType=\"" + targetPackage + "." + classNameUpperCase + "\">");
            lines.add("SELECT * FROM " + tableName);
            lines.add("<where>");
            for (Pair<String, String> pair : entry.getValue()) {
                String columnName = pair.getKey();
                lines.add("<if test=\"" + columnName + " !=null and " + columnName + "!=''\">and " + columnName + "=#{" + columnName + "}</if>");
            }
            lines.add("</where>");
            lines.add("</select>\r\n");

            //insert
            lines.add("<insert id=\"save" + classNameUpperCase + "\" parameterType=\"" + targetPackage + "." + classNameUpperCase + "\" useGeneratedKeys=\"true\" keyProperty=\"" + getPrimaryKey(entry) + "\">");
            String insertSql = "INSERT INTO " + tableName + " (";
            int size = entry.getValue().size();
            for (Pair<String, String> pair : entry.getValue()) {
                if (--size != 0) {
                    insertSql += pair.getKey() + ", ";
                } else {
                    insertSql += pair.getKey() + ") ";
                }
            }
            insertSql += "VALUE (";
            size = entry.getValue().size();
            for (Pair<String, String> pair : entry.getValue()) {
                if (--size != 0) {
                    insertSql += "#{" + pair.getKey() + "}, ";
                } else {
                    insertSql += "#{" + pair.getKey() + "}) ";
                }
            }
            lines.add(insertSql);
            lines.add("</insert> \r\n");
            //delete
            lines.add("<delete id=\"delete" + classNameUpperCase + "\" parameterType=\"" + targetPackage + "." + classNameUpperCase + "\">");
            lines.add("DELETE FROM " + tableName + " WHERE " + primaryKey + "=#{" + primaryKey + "}");
            lines.add("</delete>\r\n");

            //update
            lines.add("<update id=\"update" + classNameUpperCase + "\"" + " parameterType=\"" + targetPackage + "." + classNameUpperCase + "\">");
            lines.add("UPDATE " + tableName);
            lines.add("<set>");
            size = entry.getValue().size();
            for (Pair<String, String> pair : entry.getValue()) {
                String columnName = pair.getKey();
                if (--size != 0) {
                    lines.add("<if test=\"" + columnName + "!=null and " + columnName + "!=''\">" + columnName + "=#{" + columnName + "},</if>");
                } else {
                    lines.add("<if test=\"" + columnName + "!=null and " + columnName + "!=''\">" + columnName + "=#{" + columnName + "}</if>");
                }
            }
            lines.add("</set>");
            lines.add("where " + primaryKey + " = #{" + primaryKey + "}");

            lines.add("</update>\r\n");
            lines.add("</mapper>");

            writeToFile("mappers", fileName, lines);
        }
    }

    /**
     * 根据获得的表,生成DAO接口
     *
     * @param map
     */
    private void generateDao(Map<String, List<Pair<String, String>>> map) {
        Iterator<Map.Entry<String, List<Pair<String, String>>>> hashMapIterator = map.entrySet().iterator();
        while (hashMapIterator.hasNext()) {
            List<String> lines = new ArrayList<>();
            lines.add("import java.util.List;\r\n");
            Map.Entry<String, List<Pair<String, String>>> entry = hashMapIterator.next();
            String className = entry.getKey();
            String classNameUpperCase = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, className);
            lines.add("public interface " + "I" + classNameUpperCase + "Dao {\r\n");
            String fileName = "I" + classNameUpperCase + "Dao.java";
            Path filePath = Paths.get(fileName);
            //TODO:缺少缩进样式
            lines.add("public List<" + classNameUpperCase + "> query" + classNameUpperCase + " (" + classNameUpperCase + " param);\r\n");
            lines.add("public " + classNameUpperCase + " save" + classNameUpperCase + "(" + classNameUpperCase + " param);\r\n");
            lines.add("public " + classNameUpperCase + " update" + classNameUpperCase + "(" + classNameUpperCase + " param);\r\n");
            lines.add("public Boolean delete" + classNameUpperCase + "(" + classNameUpperCase + " param);\r\n");
            lines.add("}\r\n");

            writeToFile("daos", fileName, lines);
        }
    }

    /**
     * 根据获得的表,生成DAO的实现类
     *
     * @param map
     */
    private void generateDaoImpl(Map<String, List<Pair<String, String>>> map) {
        Iterator<Map.Entry<String, List<Pair<String, String>>>> hashMapIterator = map.entrySet().iterator();
        while (hashMapIterator.hasNext()) {
            Map.Entry<String, List<Pair<String, String>>> entry = hashMapIterator.next();
            String className = entry.getKey();
            String classNameUpperCase = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, className);
            String fileName = classNameUpperCase + "DaoImpl.java";
            List<String> lines = new ArrayList<>();
            lines.add("import com.fclassroom.mis.entity.km.Api;");
            lines.add("import org.mybatis.spring.SqlSessionTemplate;");
            lines.add("import org.springframework.stereotype.Repository;");
            lines.add("import javax.annotation.Resource;");
            lines.add("import java.util.List;\r\n");
            lines.add("@Repository");
            lines.add("public class " + classNameUpperCase + "DaoImpl implements I" + classNameUpperCase + "Dao {\r\n");
            lines.add("@Resource(name=\""+sqlSession+"\")");
            lines.add("private SqlSessionTemplate "+sqlSession+";\r\n");
            //public List<T> queryT(T param)
            lines.add("@Override");
            lines.add("public List<" + classNameUpperCase + "> query" + classNameUpperCase + "(" + classNameUpperCase + " param) {");
            lines.add("return "+sqlSession+".selectList(\"I" + classNameUpperCase + "Dao.query" + classNameUpperCase + "\", param);");
            lines.add("}\r\n");
            //public T saveT(T param)
            lines.add("@Override");
            lines.add("public " + classNameUpperCase + " save" + classNameUpperCase + "(" + classNameUpperCase + " param) {");
            lines.add(""+sqlSession+".insert(\"I" + classNameUpperCase + "Dao.save" + classNameUpperCase + "\", param);");
            lines.add("return param;");
            lines.add("}\r\n");
            //public T updateT(T param)
            lines.add("@Override");
            lines.add("public " + classNameUpperCase + " update" + classNameUpperCase + "(" + classNameUpperCase + " param) {");
            lines.add(""+sqlSession+".update(\"I" + classNameUpperCase + "Dao.update" + classNameUpperCase + "\", param);");
            lines.add("return param;");
            lines.add("}\r\n");
            //public Boolean deleteT(T param)
            lines.add("@Override");
            lines.add("public Boolean delete" + classNameUpperCase + "(" + classNameUpperCase + " param) {");
            lines.add("return ("+sqlSession+".delete(\"I" + classNameUpperCase + "Dao.delete" + classNameUpperCase + "\", param) != 0);");
            lines.add("}\r\n");
            lines.add("}");

            writeToFile("daoImpls", fileName, lines);
        }
    }

    private void generateService(Map<String, List<Pair<String, String>>> map) {
        Iterator<Map.Entry<String, List<Pair<String, String>>>> hashMapIterator = map.entrySet().iterator();
        while (hashMapIterator.hasNext()) {
            List<String> lines = new ArrayList<>();
            Map.Entry<String, List<Pair<String, String>>> entry = hashMapIterator.next();
            String className = entry.getKey();
            String classNameUpperCase = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, className);
            String fileName = "I" + classNameUpperCase +"Service.java";
            Path filePath = Paths.get(fileName);

            lines.add("public interface I"+classNameUpperCase+"Service {");
            //query
            String queryParams = "";
//            lines.add("public List<"+classNameUpperCase+"> query"+classNameUpperCase+"(");
            queryParams +="public List<"+classNameUpperCase+"> query"+classNameUpperCase+"(";
            int size = entry.getValue().size();
            for (Pair<String,String> pair:entry.getValue()) {
                if (--size != 0) {
                    queryParams += pair.getValue() + " " + pair.getKey() +",";
                } else {
                    queryParams += pair.getValue() + " " +pair.getKey() + ") throws MonitorException;";
                }
            }

            lines.add(queryParams);
            lines.add("}");

            writeToFile("service", fileName, lines);
        }
    }

    private void generateServiceImpl(Map<String, List<Pair<String, String>>> map) {
        Iterator<Map.Entry<String, List<Pair<String, String>>>> hashMapIterator = map.entrySet().iterator();
        while (hashMapIterator.hasNext()) {
            List<String> lines = new ArrayList<>();
            Map.Entry<String, List<Pair<String, String>>> entry = hashMapIterator.next();
            String className = entry.getKey();
            String classNameUpperCase = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL, className);
            String fileName = classNameUpperCase +"ServiceImpl.java";
            Path filePath = Paths.get(fileName);

            lines.add("@Service");
            lines.add("public class "+classNameUpperCase+"ServiceImpl implements I"+classNameUpperCase+"Service{");
            lines.add("@Autowired");
            lines.add("private I"+classNameUpperCase+"Dao "+className+"Dao;");
            //query
            lines.add("@Override");
//            lines.add("public List<"+classNameUpperCase+"> query"+classNameUpperCase+"(");

            String queryParams="public List<"+classNameUpperCase+"> query"+classNameUpperCase+"(";
            int size = entry.getValue().size();
            for (Pair<String,String> pair:entry.getValue()) {
                if (--size != 0) {
                    queryParams += pair.getValue() + " " + pair.getKey() +",";
                } else {
                    queryParams += pair.getValue() + " " +pair.getKey() + ") throws MonitorException {";
                }
            }
            lines.add(queryParams);
            lines.add(classNameUpperCase+" param = new "+classNameUpperCase+"();");
            for (Pair<String,String> pair:entry.getValue()) {

                lines.add("param.set"+CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_CAMEL,pair.getKey())+"("+pair.getKey()+");");
            }
            lines.add("try {");
            lines.add("return "+className+"Dao.query"+classNameUpperCase+"(param);");
            lines.add("} catch (Exception e) {");
            lines.add("throw new MonitorException(Errorcode.Operate_fail);");
            lines.add("}");
            lines.add("}");
            lines.add("}");

            writeToFile("serviceImpl", fileName, lines);
        }
    }

    /**
     * 将数据库中的数据类型,转化为java中的数据类型
     * 同时将表名从下划线,转化成大写驼峰命名
     *
     * @param map
     * @return
     */
    private Map<String, List<Pair<String, String>>> datatypeConvert(Map<String, List<Pair<String, String>>> map) {
        Iterator<Map.Entry<String, List<Pair<String, String>>>> hashMapIterator = map.entrySet().iterator();
        Map<String, List<Pair<String, String>>> resultMap = new HashMap<>();
        while (hashMapIterator.hasNext()) {
            Map.Entry<String, List<Pair<String, String>>> entry = hashMapIterator.next();
            List<Pair<String, String>> columns = new ArrayList<>();
            for (Pair<String, String> pair : entry.getValue()) {
                Pair<String, String> pairResult;
                if ("bigint".equals(pair.getValue())) {
                    pairResult = new Pair<String, String>(pair.getKey(), "Long");
                } else if ("tinyint".equals(pair.getValue())) {
                    pairResult = new Pair<String, String>(pair.getKey(), "Byte");
                } else if ("varchar".equals(pair.getValue()) || "char".equals(pair.getValue()) || "text".equals(pair.getValue())) {
                    pairResult = new Pair<String, String>(pair.getKey(), "String");
                } else if ("datetime".equals(pair.getValue()) || "date".equals(pair.getValue()) || "timestamp".equals(pair.getValue())) {
                    pairResult = new Pair<String, String>(pair.getKey(), "Date");
                } else if ("int".equals(pair.getValue())) {
                    pairResult = new Pair<String, String>(pair.getKey(), "Integer");
                } else {
                    pairResult = pair;
                }
                columns.add(pairResult);
            }
            //将表名从下划线,转换成驼峰命名
            String tableNameCamelCase = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, entry.getKey());
            resultMap.put(tableNameCamelCase, columns);
        }

//        printMap(resultMap);

        return resultMap;
    }

    /**
     * 遍历显示传入的map的所有内容
     *
     * @param map
     */
    private void printMap(Map<String, List<Pair<String, String>>> map) {
        Iterator<Map.Entry<String, List<Pair<String, String>>>> hashMapIterator = map.entrySet().iterator();
        while (hashMapIterator.hasNext()) {
            Map.Entry<String, List<Pair<String, String>>> entry = hashMapIterator.next();
            System.out.println("key: " + entry.getKey());
            System.out.println("value: " + entry.getValue());
        }
    }

    /**
     * 从传入的entry(即表示一张表的iterable)中取出主键
     *
     * @param entry
     * @return
     */
    private String getPrimaryKey(Map.Entry<String, List<Pair<String, String>>> entry) {
        return entry.getValue().get(0).getKey();
    }

    /**
     * 将生成的输入流写入文件
     *
     * @param pathName
     * @param fileName
     * @param lines
     */
    private void writeToFile(String pathName, String fileName, List<String> lines) {
        try {
            String path = "target/generated-sources/simpleMapperGenerator/" + pathName + "/";
            File parentPath = new File(path);
            parentPath.mkdirs();
            Path filePath = Paths.get(parentPath + "/" + fileName);
            Files.write(filePath, lines, Charset.forName("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 对外的接口,执行以生成文件
     */
    public void generateFiles() {
        Map<String, List<Pair<String, String>>> map = getColumns();
        Map<String, List<Pair<String, String>>> convertedMap = datatypeConvert(map);
//        printMap(convertedMap);
        generateEntity(convertedMap);
        generateDao(convertedMap);
        generateDaoImpl(convertedMap);
        generateMapper(convertedMap);
        generateService(convertedMap);
        generateServiceImpl(convertedMap);
        System.out.println("生成成功 generate success");

    }
}
