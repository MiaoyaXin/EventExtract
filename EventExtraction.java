import com.hankcs.AHANLP;
import com.hankcs.Config;
import com.hankcs.lda.Corpus;
import com.hankcs.lda.LDAModel;
import com.hankcs.lda.LdaGibbsSampler;
import com.hankcs.lda.LdaUtil;
import com.hankcs.seg.NER;
import com.hankcs.seg.NERTerm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.lang.System.exit;

public class EventExtraction {
    /**
     * 事件描述句构造，事件抽取原理
     * 1.包含时间和人名或机构实体的长句
     * 2.包含主题的长句
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception{
        // 加载主题模型及其矩阵
        LDAModel ldaModel = new LDAModel(Config.hanLDAModelPath());
        double[][] phiV1 = ldaModel.getPhiMatrix();
        // 读取文章存入列表
        List<String> articles = new ArrayList<>();
        articles = EventTools.getArticles();
        // 构建停用词集合
        List<String> stopwords = new ArrayList<>();
        stopwords = EventTools.getStopLst();
        // 事件抽取方法
        List<List<String>> eventList = new ArrayList<>();
        eventList = eventExtract(
                articles,
                ldaModel, phiV1,
                stopwords);
        if (eventList.size() != articles.size()){
            System.err.println("事件列表与文章列表尺寸不一致！");
            System.out.println(eventList.size());
            System.out.println(articles.size());
            exit(1);
        }
        printEventList(eventList, articles);
    }

    /**
     * 打印事件列表
     * @param pLst 事件列表
     */
    public static void printEventList(List<List<String>> pLst, List<String> articles){
        for(int idx = 0; idx < pLst.size(); ++idx) {
            System.out.println("ID:\t" + String.valueOf(idx + 1));
            System.out.println("导语:\t"
                    + articles.get(idx).substring(0, Integer.min(16, articles.get(idx).length())));
            System.out.println("事件:");

            for (String ss : pLst.get(idx))
                System.out.println(ss);

            System.out.println();
        }
    }

    /**
     * 抽取事件，描述句列表描述事件
     * @param articles
     * @param ldaModel
     * @param phi
     * @param stopwords
     * @return
     * @throws IOException
     */
    public static List<List<String>> eventExtract(List<String> articles,
                                            LDAModel ldaModel, double[][] phi,
                                            List<String> stopwords)throws IOException {
        // 抽取主题
        List<List<String>> texts = new ArrayList<>();
        texts = EventTools.txtWash(articles, stopwords);
        // 1.Load corpus from disk
        Corpus txts = loadDocs(texts);
        if (txts == null) return null;
        // 2.Create a LDA sampler
        LdaGibbsSampler ldaGibbsSampler1 = new LdaGibbsSampler(txts.getDocument(), txts.getVocabularySize());
        // 3.Train it
        int kTopics = texts.size();
        ldaGibbsSampler1.gibbs(kTopics);
        // 4.The phi matrix is a LDA model, you can use LdaUtil to explain it.
        double[][] phi1 = ldaGibbsSampler1.getPhi();
        Map<String, Double>[] topicMap1 = LdaUtil.translate(phi1, txts.getVocabulary(), 64);
        List<List<List<String>>> topicsList = explain(topicMap1);

        // 抽取包含时间和主体的长句，生成候选事件描述句
        List<List<String>> candidateEventList = new ArrayList<>();
        candidateEventList = generateCandidateEvents(articles);

        if (topicsList.size() != candidateEventList.size()) {
            System.err.println("主题列表与事件列表尺寸不一致！");
            System.out.println(topicsList.size());
            System.out.println(candidateEventList.size());
            exit(1);
        }

        // 事件列表
        List<List<String>> eventList = new ArrayList<>();
        eventList = generateEvents(candidateEventList, topicsList);

        return eventList;
    }

    /**
     * 生成候选事件列表，每个事件列表又是长句列表
     * @param articles
     * @return 候选事件列表[[长句列表], []]
     */
    public static List<List<String>> generateCandidateEvents(List<String> articles) {
        List<List<String>> candidateEvents = new ArrayList<>();
        for (int idx=0; idx < articles.size(); ++idx) {
            candidateEvents.add(getLongSentences(articles.get(idx)));
        }
        return candidateEvents;
    }

    /**
     * 生成包含时间和人名或组织机构实体的长句子列表
     * @param article
     * @return
     */
    public static List<String> getLongSentences(String article){
        List<String> sentenceList = new ArrayList<>();

        if (article == null || article.isEmpty())
            return sentenceList;

        String newArticle = article.replaceAll("(：\\n)+", "：").replaceAll("(:\\n)+", ":");
        for (String ss: newArticle.split("[。？！\\n]+")){
            if (ss.length() < 16) continue;
            // 判断该句子是否包含时间
            List<NERTerm> NERResult = AHANLP.NER(ss);
            // 时间信息
            if (NER.getTimeInfo(NERResult).size() > 0 &&
                    (NER.getOrgInfo(NERResult).size() > 0 || NER.getPerInfo(NERResult).size() > 0)) {
                // System.out.println(ss);
                sentenceList.add(EventTools.removeAllBlank(ss));
            }
        }

        if (sentenceList.size() < 1) {
            for (String ss: newArticle.split("[。？！\\n]+")){
                if (ss.length() < 16) continue;
                // 判断该句子是否包含时间
                List<NERTerm> NERResult = AHANLP.NER(ss);
                // 时间信息
                if (NER.getTimeInfo(NERResult).size() > 0) {
                    sentenceList.add(EventTools.removeAllBlank(ss));
                }
            }
        }
        return sentenceList;
    }

    /**
     * 根据主题，对文章列表的候选事件描述句进行过滤
     * @param candidateEventList
     * @param topicsList
     * @return  事件描述句列表
     */
    public static List<List<String>> generateEvents(
            List<List<String>> candidateEventList,
            List<List<List<String>>> topicsList)
    {
        List<List<String>> eventList = new ArrayList<>();
        String zeroStr = "^[\n\r\t]+$";
        Pattern pZero = Pattern.compile(zeroStr);
        String skipStr1 = "来源|编辑|作者|供稿";
        Pattern pSkip1 = Pattern.compile(skipStr1);

        for (int idx = 0; idx < candidateEventList.size(); ++idx){
            List<String> events = new ArrayList<>();
            for (String sentence: candidateEventList.get(idx)) {

                if (pZero.matcher(sentence).find() || pSkip1.matcher(sentence).find())
                    continue;

                for (String t_ : topicsList.get(idx).get(0)) {
                    if (sentence.contains(t_) &&
                            !events.contains(
                                    EventTools.removeBorderPunc(EventTools.removeAllBlank(sentence)) + "。"))
                    {
                        events.add(EventTools.removeBorderPunc(EventTools.removeAllBlank(sentence)) + "。");
                    }
                }

                if (events.size() > 2)
                    break;
            }
            if (events.size() < 1 && candidateEventList.get(idx).size() > 0) {
                events.add(EventTools.removeBorderPunc(EventTools.removeAllBlank(candidateEventList.get(idx).get(0)))+ "。");
            }
            eventList.add(events);
        }

        return eventList;
    }

    /**
     * 清洗后的文档转换为Corpus类型对象
     * @param texts
     * @return
     * @throws IOException
     */
    public static Corpus loadDocs(List<List<String>> texts) throws IOException
    {
        Corpus corpus = new Corpus();

        for (List<String> wordList: texts)
            corpus.addDocument(wordList);

        if (corpus.getVocabularySize() == 0) return null;

        return corpus;
    }

    /**
     * 提取所有主题的主题词及相关值，三维列表存储
     * @param result
     * @return
     */
    public static List<List<List<String>>> explain(Map<String, Double>[] result)
    {
        List<List<List<String>>> topicsList = new ArrayList<>();
        for (Map<String, Double> topicMap : result)
        {
            topicsList.add(explain(topicMap));
        }
        return topicsList;
    }

    /**
     * 提取某个主题下的主题词及其相关值，二维列表存储
     * @param topicMap
     * @return
     */
    public static List<List<String>> explain(Map<String, Double> topicMap)
    {
        List<List<String>> topicWordsAndValue = new ArrayList<>();
        List<String> words = new ArrayList<>();
        List<String> values = new ArrayList<>();

        for (Map.Entry<String, Double> entry : topicMap.entrySet())
        {
            words.add(entry.getKey());
            values.add(String.valueOf(entry.getValue()));
        }

        topicWordsAndValue.add(words);
        topicWordsAndValue.add(values);

        return topicWordsAndValue;
    }
}
