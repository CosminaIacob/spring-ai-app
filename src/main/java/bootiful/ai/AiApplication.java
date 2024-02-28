package bootiful.ai;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }

    @Component
    static class CarinaAIClient {

        private final VectorStore vectorStore;

        private final ChatClient chatClient;

        CarinaAIClient(VectorStore vectorStore, ChatClient chatClient) {
            this.vectorStore = vectorStore;
            this.chatClient = chatClient;
        }

        String chat(String message) {
            var prompt = """
                                        
                    You're assisting with questions about services offered by Carina.
                    Carina is a two-sided healthcare marketplace focusing on home care aides (caregivers)
                    and their Medicaid in-home care clients (adults and children with developmental disabilities and low income elderly population).
                    Carina's mission is to build online tools to bring good jobs to care workers, so care workers can provide the
                    best possible care for those who need it.
                                       
                    Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                    If unsure, simply state that you don't know.
                                       
                    DOCUMENTS:
                    {documents}
                                        
                    """;

            var listOfSimilarDocs = this.vectorStore.similaritySearch(message);
            var docs = listOfSimilarDocs.stream()
                    .map(Document::getContent)
                    .collect(Collectors.joining(System.lineSeparator()));
            var sytemMessage = new SystemPromptTemplate(prompt)
                    .createMessage(Map.of("documents", docs));
            var userMessage = new UserMessage(message);
            var promptList = new Prompt(List.of(sytemMessage, userMessage));

            var aiResponse = this.chatClient.call(promptList);

            return aiResponse.getResult().getOutput().getContent();
        }
    }

    @Bean
    ApplicationRunner demo(VectorStore vectorStore,
                           @Value("file:C:\\Users\\coiacob\\Projects\\Tutorials\\ai\\medicaid-wa-faqs.pdf") Resource pdf,
                           JdbcTemplate template,
                           ChatClient chatClient,
                           CarinaAIClient carinaAIClient) {
        return args -> {

//            setup(vectorStore, pdf, template);

            BufferedWriter writer = new BufferedWriter(new FileWriter("output.txt"));

            writer.write(
                    carinaAIClient.chat("""
                            What should I know about the transition to Consumer Direct Care Network Washington (CDWA)?
                            """)
            );
            writer.close();

//            System.out.println(
//                    carinaAIClient.chat("""
//                            What should I know about the transition to Consumer Direct Care Network Washington (CDWA)?
//                            """)
//            );
        };
    }

    private static void setup(VectorStore vectorStore, Resource pdf, JdbcTemplate template) {
        template.update("delete from vector_store");

        var config = PdfDocumentReaderConfig
                .builder()
                .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
                        .withNumberOfBottomTextLinesToDelete(3)
                        .build())
                .build();
        var pdfReader = new PagePdfDocumentReader(pdf, config);

        var textSplitter = new TokenTextSplitter();

        var docs = textSplitter.apply(pdfReader.get());
        vectorStore.accept(docs);
    }
}
