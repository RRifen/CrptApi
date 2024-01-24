package org.example;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {

  public static class Document {
    public static class Description {
      @SerializedName("participantInn")
      private String participantInn;

      public Description(String participantInn) {
        this.participantInn = participantInn;
      }
    }
    public static class Product {
      private String certificateDocument;
      private LocalDate certificateDocumentDate;
      private String certificateDocumentNumber;
      private String ownerInn;
      private String producerInn;
      private LocalDate productionDate;
      private String tnvedCode;
      private String uitCode;
      private String uituCode;

      public Product setCertificateDocument(String certificateDocument) {
        this.certificateDocument = certificateDocument;
        return this;
      }

      public Product setCertificateDocumentDate(LocalDate certificateDocumentDate) {
        this.certificateDocumentDate = certificateDocumentDate;
        return this;
      }

      public Product setCertificateDocumentNumber(String certificateDocumentNumber) {
        this.certificateDocumentNumber = certificateDocumentNumber;
        return this;
      }

      public Product setOwnerInn(String ownerInn) {
        this.ownerInn = ownerInn;
        return this;
      }

      public Product setProducerInn(String producerInn) {
        this.producerInn = producerInn;
        return this;
      }

      public Product setProductionDate(LocalDate productionDate) {
        this.productionDate = productionDate;
        return this;
      }

      public Product setTnvedCode(String tnvedCode) {
        this.tnvedCode = tnvedCode;
        return this;
      }

      public Product setUitCode(String uitCode) {
        this.uitCode = uitCode;
        return this;
      }

      public Product setUituCode(String uituCode) {
        this.uituCode = uituCode;
        return this;
      }
    }
    public enum DocType {
      LP_INTRODUCE_GOODS
    }

    private Description description;
    private String docId;
    private String docStatus;
    private DocType docType;
    @SerializedName("importRequest")
    private boolean importRequest;
    private String ownerInn;
    private String participantInn;
    private String producerInn;
    private LocalDate productionDate;
    private String productionType;
    private List<Product> products;
    private LocalDate regDate;
    private String regNumber;

    public Document setDescription(Description description) {
      this.description = description;
      return this;
    }

    public Document setDocId(String docId) {
      this.docId = docId;
      return this;
    }

    public Document setDocStatus(String docStatus) {
      this.docStatus = docStatus;
      return this;
    }

    public Document setDocType(DocType docType) {
      this.docType = docType;
      return this;
    }

    public Document setImportRequest(boolean importRequest) {
      this.importRequest = importRequest;
      return this;
    }

    public Document setOwnerInn(String ownerInn) {
      this.ownerInn = ownerInn;
      return this;
    }

    public Document setParticipantInn(String participantInn) {
      this.participantInn = participantInn;
      return this;
    }

    public Document setProducerInn(String producerInn) {
      this.producerInn = producerInn;
      return this;
    }

    public Document setProductionDate(LocalDate productionDate) {
      this.productionDate = productionDate;
      return this;
    }

    public Document setProductionType(String productionType) {
      this.productionType = productionType;
      return this;
    }

    public Document setProducts(List<Product> products) {
      this.products = products;
      return this;
    }

    public Document setRegDate(LocalDate regDate) {
      this.regDate = regDate;
      return this;
    }

    public Document setRegNumber(String regNumber) {
      this.regNumber = regNumber;
      return this;
    }
  }

  private static class LocalDateSerializer implements JsonSerializer<LocalDate> {
    @Override
    public JsonElement serialize(LocalDate localDate, Type type, JsonSerializationContext jsonSerializationContext) {
      return new JsonPrimitive(localDate.toString());
    }
  }

  public static class ApiRequests {
    public static HttpRequest createDocumentPostRequest(Document document, Gson gson) {
      return HttpRequest.newBuilder()
          .uri(createUriDocumentPostRequest())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers
              .ofString(gson.toJson(document)))
          .build();
    }

    private static URI createUriDocumentPostRequest() {
      try {
        return new URI("https://ismp.crpt.ru/api/v3/lk/documents/create");
      } catch (URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private final int requestLimit;
  private final long requestsTimeLimit;
  private final HttpClient httpClient;
  private final Queue<Long> timestampQueue = new LinkedList<>();
  private final Lock lock = new ReentrantLock(true);
  private final Gson gson;

  public CrptApi(TimeUnit timeUnit, int requestLimit) {
    this.requestLimit = requestLimit;
    requestsTimeLimit = TimeUnit.MILLISECONDS.convert(1, timeUnit);
    gson = createGsonForDocument();
    httpClient = HttpClient.newHttpClient();
  }

  private Gson createGsonForDocument() {
    return new GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(LocalDate.class, new LocalDateSerializer())
        .create();
  }

  public HttpResponse<String> sendDocumentRequest(Document document, String signature) throws IOException, InterruptedException {
    validateRateLimit();

    HttpRequest httpRequest = ApiRequests.createDocumentPostRequest(document, gson);
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private void validateRateLimit() throws InterruptedException {
    try {
      lock.lock();
      while (!timestampQueue.isEmpty() && timestampQueue.peek() < (System.currentTimeMillis() - requestsTimeLimit)) {
        timestampQueue.remove();
      }
      while (requestLimit <= timestampQueue.size()) {
        long waiting = timestampQueue.peek() - (System.currentTimeMillis() - requestsTimeLimit);
        while(waiting > 0) {
          Thread.sleep(waiting);
          waiting = timestampQueue.peek() - (System.currentTimeMillis() - requestsTimeLimit);
        }
        timestampQueue.remove();
      }
      timestampQueue.add(System.currentTimeMillis());
    }
    finally {
      lock.unlock();
    }
  }

}
