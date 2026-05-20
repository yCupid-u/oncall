import com.tencentcloudapi.cls.v20201016.ClsClient;
import com.tencentcloudapi.cls.v20201016.models.CreateIndexRequest;
import com.tencentcloudapi.cls.v20201016.models.FullTextInfo;
import com.tencentcloudapi.cls.v20201016.models.KeyValueInfo;
import com.tencentcloudapi.cls.v20201016.models.RuleInfo;
import com.tencentcloudapi.cls.v20201016.models.RuleKeyValueInfo;
import com.tencentcloudapi.cls.v20201016.models.ValueInfo;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SeedClsIndex {
    private static final String TOKENIZER = "@&?|#()='\\\",;:<>[]{} \\n\\t\\r";

    public static void main(String[] args) throws Exception {
        String secretId = env("TENCENTCLOUD_SECRET_ID");
        String secretKey = env("TENCENTCLOUD_SECRET_KEY");
        String region = getenv("TENCENT_CLS_DEFAULT_REGION", "ap-guangzhou");

        Map<String, String> topics = new HashMap<>();
        topics.put("application-logs", env("TENCENT_CLS_TOPIC_APPLICATION_LOGS"));
        topics.put("system-events", env("TENCENT_CLS_TOPIC_SYSTEM_EVENTS"));
        topics.put("database-slow-query", env("TENCENT_CLS_TOPIC_DATABASE_SLOW_QUERY"));

        Credential credential = new Credential(secretId, secretKey);
        HttpProfile httpProfile = new HttpProfile();
        httpProfile.setEndpoint("cls.tencentcloudapi.com");
        ClientProfile clientProfile = new ClientProfile();
        clientProfile.setHttpProfile(httpProfile);
        ClsClient client = new ClsClient(credential, region, clientProfile);

        for (Map.Entry<String, String> entry : topics.entrySet()) {
            try {
                CreateIndexRequest request = new CreateIndexRequest();
                request.setTopicId(entry.getValue());
                request.setStatus(true);
                request.setIncludeInternalFields(true);
                request.setRule(rule(entry.getKey()));
                client.CreateIndex(request);
                System.out.println("INDEX_OK " + entry.getKey() + " " + entry.getValue());
            } catch (TencentCloudSDKException e) {
                if (e.getErrorCode() != null && e.getErrorCode().contains("IndexConflict")) {
                    System.out.println("INDEX_EXISTS " + entry.getKey() + " " + entry.getValue());
                } else {
                    throw e;
                }
            }
        }
    }

    private static RuleInfo rule(String topicName) {
        FullTextInfo fullText = new FullTextInfo();
        fullText.setCaseSensitive(false);
        fullText.setTokenizer(TOKENIZER);
        fullText.setContainZH(true);

        RuleKeyValueInfo keyValue = new RuleKeyValueInfo();
        keyValue.setCaseSensitive(false);
        List<KeyValueInfo> fields = new ArrayList<>();
        add(fields, "level", text());
        add(fields, "service", text());
        add(fields, "instance", text());
        add(fields, "message", text());
        add(fields, "trace_id", text());

        if ("application-logs".equals(topicName)) {
            add(fields, "response_time", longType());
            add(fields, "endpoint", text());
        } else if ("system-events".equals(topicName)) {
            add(fields, "event", text());
            add(fields, "namespace", text());
        } else if ("database-slow-query".equals(topicName)) {
            add(fields, "db", text());
            add(fields, "query_time", doubleType());
            add(fields, "lock_time", doubleType());
            add(fields, "rows_examined", longType());
            add(fields, "full_table_scan", text());
        }

        keyValue.setKeyValues(fields.toArray(new KeyValueInfo[0]));
        RuleInfo rule = new RuleInfo();
        rule.setFullText(fullText);
        rule.setKeyValue(keyValue);
        return rule;
    }

    private static void add(List<KeyValueInfo> fields, String key, ValueInfo value) {
        KeyValueInfo info = new KeyValueInfo();
        info.setKey(key);
        info.setValue(value);
        fields.add(info);
    }

    private static ValueInfo text() {
        ValueInfo value = new ValueInfo();
        value.setType("text");
        value.setTokenizer(TOKENIZER);
        value.setSqlFlag(true);
        value.setContainZH(true);
        return value;
    }

    private static ValueInfo longType() {
        ValueInfo value = new ValueInfo();
        value.setType("long");
        value.setSqlFlag(true);
        return value;
    }

    private static ValueInfo doubleType() {
        ValueInfo value = new ValueInfo();
        value.setType("double");
        value.setSqlFlag(true);
        return value;
    }

    private static String env(String key) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing env var: " + key);
        }
        return value;
    }

    private static String getenv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.trim().isEmpty() ? defaultValue : value;
    }
}
