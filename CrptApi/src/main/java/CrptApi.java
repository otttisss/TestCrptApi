import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Подключение сторонних библиотек для работы с JSON и HTTP
// Подключались в dependencies Maven в pom файле
import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final AtomicInteger requestCost;
    private final Object lock = new Object();
    private static final String API_URL = "https://ismp.crpt.ru/api/v3";
    private final Gson gson;
    private final CloseableHttpClient httpClient;

    //Конструктор класса в котором происходит инициализация полей
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        this.requestCost = new AtomicInteger(0);
        this.gson = new Gson();
        this.httpClient = HttpClients.createDefault();
    }

    //Метод который нужно реализовать по ТЗ (создание документа и отправка запроса к API)
    public void createDocument(Object document, String signature) throws IOException {
        // Синхронизация на объекте для работы с несколькими потоками
        synchronized (lock) {
            int currentRequesCount = requestCost.get(); //Получение текущего количества запросов
            long startTime = System.currentTimeMillis(); //Текущее время
            long currentTime = startTime;

            while (currentRequesCount >= requestLimit) {
                //Рассчет оставшегося времени до окончания времени
                long elapsedTime = currentTime - startTime;
                long timeRemaining = timeUnit.toMillis(1) - elapsedTime;

                if (timeRemaining <= 0) { // При отрицательном времени сбрасываем счетчик запросов т.к. интервал истек
                    requestCost.set(0);
                    break;
                }

                // Здесь происходит ожидание окончания интервала или до notifyAll
                try {
                    lock.wait(timeRemaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // Перерассчет кол-ва запросов и времени
                currentRequesCount = requestCost.get();
                currentTime = System.currentTimeMillis();
            }

            String jsonDoc = gson.toJson(document); // Преобразование документа в json строку

            // Создание http post запроса и в тело запроса подаем json документ
            HttpPost httpPost = new HttpPost(API_URL);
            httpPost.setEntity(new StringEntity(jsonDoc));

//            httpPost.addHeader();
            // Выполение запроса и получение ответа
            HttpResponse httpResponse = httpClient.execute(httpPost);
            HttpEntity httpEntity = httpResponse.getEntity();
            if (httpEntity != null) {
                String responseBody = EntityUtils.toString(httpEntity);
            }

            requestCost.incrementAndGet(); //Увеличение счетчика запросов
            lock.notifyAll(); // Оповещение потоков
        }
    }
}
