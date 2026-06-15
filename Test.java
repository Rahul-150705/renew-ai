import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

public class Test {
    public static void main(String[] args) throws Exception {
        PDDocument doc = Loader.loadPDF(new byte[0]);
    }
}
