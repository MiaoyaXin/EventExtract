import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.corpus.document.sentence.word.Word;
import com.hankcs.hanlp.model.perceptron.PerceptronLexicalAnalyzer;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EventTools {

    /**
     * 读取测试文章列表
     * @return
     * @throws IOException
     */
    public static List<String> getArticles() throws IOException {
        List<String> articles = new ArrayList<>();
        File folder = new File("data/articlebak");
        // File folder = new File("data/jinrong");
        for (File file: folder.listFiles()){

            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
            String line;
            String lines = "";
            while ((line = br.readLine()) != null){
                if (line.trim().length() < 2) continue;
                lines +=  line + "\n";
            }
            br.close();
            articles.add(lines);
            // System.out.println(file);
        }
        // System.out.println(articles.size());
        return articles;
    }

    /**
     * 构建停用词集合
     * @return
     * @throws IOException
     */
    public static List<String> getStopLst() throws IOException{
        List<String> tmpLst = new ArrayList<>();
        File folder = new File("data/stopwords");
        for (File file: folder.listFiles()){
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "utf-8"));
            String line;
            while ((line = br.readLine()) != null){
                if (line.trim().length() < 1) continue;
                tmpLst.add(line);
            }
            br.close();
        }
        return tmpLst;
    }

    /**
     * 感知器分词，加载人民日报训练的分词模型，去停用词，去单字词
     * @param articles
     * @return
     * @throws IOException
     */
    public static List<List<String>> txtWash(List<String> articles, List<String> stopLst) throws IOException
    {
        PerceptronLexicalAnalyzer analyzer = new PerceptronLexicalAnalyzer(
                "data/model/perceptron/pku199801/cws.bin",
                HanLP.Config.PerceptronPOSModelPath,
                HanLP.Config.PerceptronNERModelPath);  // 感知器分词
        List<List<String>> docs = new ArrayList<>();
        String emailRegEx ="^[a-zA-Z0-9_.-]+@([a-zA-Z0-9-]+\\.)+[a-zA-Z0-9]{2,4}$";
        String dateRegex= "[\\d]+[年月日]$";
        String digitRegex = "^[\\-\\\\\\d]+$";
        String urlRegex =
                "(http://|ftp://|https://|www){0,1}[^\u4e00-\u9fa5\\s]*?\\.(com|net|cn|me|tw|fr)[^\u4e00-\u9fa5\\s]*";
        Pattern pUrlEx = Pattern.compile(urlRegex);
        Pattern pEmail = Pattern.compile(emailRegEx);
        Pattern pDate = Pattern.compile(dateRegex);
        Pattern pDigit = Pattern.compile(digitRegex);
        for (String doc: articles){
            List<String> tmpDoc = new ArrayList<>();
            for (String line: doc.split("\n")){
                for (Word w: analyzer.analyze(line.trim()).toSimpleWordList()){
                    String Wd = removeBorderBlank(w.toString().substring(0, w.toString().indexOf("/")).trim());
                    if (Wd.length() < 2 || stopLst.contains(Wd) || pDate.matcher(Wd).find()
                            ||  pEmail.matcher(Wd).find() || pDigit.matcher(Wd).find() || pUrlEx.matcher(Wd).find())
                        continue;
                    tmpDoc.add(Wd);
                }
            }
            docs.add(tmpDoc);
        }
        return docs;
    }

    /**
     * 去除字符串中所包含的空格（包括:空格(全角，半角)、制表符、换页符等）
     * @params
     * @return
     */
    public static String removeAllBlank(String s){
        String result = "";
        if(null != s && !"".equals(s)){
            result = s.replaceAll("[　 \\s*]*", "");
        }
        return result;
    }

    /**
     * 去除字符串中头部和尾部所包含的空格（包括:空格(全角，半角)、制表符、换页符等）
     * @params
     * @return
     */
    public static String removeBorderBlank(String s){
        String result = "";
        if(null != s && !"".equals(s)){
            result = s.replaceAll("^[　 \\s*]*", "").replaceAll("[　 \\s*]*$", "");
        }
        return result;
    }

    /**
     * 移除两端的标点等特殊符号
     * @param s
     * @return
     */
    public static String removeBorderPunc(String s){
        return s.replaceAll("[,，;'`?:：\"{}~!@#$%^&=_+.。；‘’【】！ …（）、]+$", "")
                .replaceAll("^[,，;'`?:：\"{}~!@#$%^&=_+.。；‘’【】！ …（）、]+", "");
    }
}
