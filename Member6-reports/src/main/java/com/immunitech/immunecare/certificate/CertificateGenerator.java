package com.immunitech.immunecare.certificate;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.immunitech.immunecare.certificate.CertificateModels.CertificateData;
import com.immunitech.immunecare.certificate.CertificateModels.DoseLine;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.ExceptionConverter;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds the printable PDF vaccination certificate (FR18): patient details, dose table, verification
 * QR code and a diagonal clinic watermark on every page. Pure function of its input - no database.
 */
@Component
public class CertificateGenerator {

    private static final Color BRAND = new Color(0, 94, 140);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, BRAND);
    private static final Font SUBTITLE = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
    private static final Font VALUE = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font TH = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

    public byte[] generate(CertificateData data) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 48, 48, 56, 60);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new PageDecorations(data.clinicName(), data.certificateId(), data.verifyUrl()));
            doc.addTitle("Vaccination Certificate " + data.certificateId());
            doc.addAuthor(data.clinicName());
            doc.addCreator("ImmuneCare");
            doc.open();

            doc.add(new Paragraph("Vaccination Certificate", TITLE));
            Paragraph clinic = new Paragraph(data.clinicName() + " | ImmuneCare Vaccination Record System", SUBTITLE);
            clinic.setSpacingAfter(14);
            doc.add(clinic);

            doc.add(patientBlock(data));
            doc.add(spacer(12));
            doc.add(new Paragraph("Vaccination history", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND)));
            doc.add(spacer(4));
            doc.add(doseTable(data));
            doc.add(spacer(18));
            doc.add(verificationBlock(data));

            doc.close();
            return out.toByteArray();
        } catch (DocumentException | IOException | WriterException e) {
            throw new IllegalStateException("Could not generate the certificate PDF", e);
        }
    }

    private PdfPTable patientBlock(CertificateData data) {
        PdfPTable t = new PdfPTable(new float[] {1.3f, 3f, 1.3f, 3f});
        t.setWidthPercentage(100);
        var p = data.patient();
        addInfo(t, "Full name", p.fullName());
        addInfo(t, "ID / passport", p.nationalId());
        addInfo(t, "Date of birth", p.dateOfBirth().format(DATE));
        addInfo(t, "Gender", p.gender() == null ? "-" : p.gender());
        addInfo(t, "Certificate ID", data.certificateId());
        addInfo(t, "Issued on", data.issuedOn().format(DATE));
        return t;
    }

    private void addInfo(PdfPTable t, String label, String value) {
        t.addCell(plainCell(label, LABEL));
        t.addCell(plainCell(value, VALUE));
    }

    private PdfPCell plainCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        c.setPaddingBottom(5);
        return c;
    }

    private PdfPTable doseTable(CertificateData data) throws DocumentException {
        PdfPTable t = new PdfPTable(new float[] {3f, 1f, 2.2f, 2.2f, 1.8f});
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String h : new String[] {"Vaccine", "Dose", "Date given", "Batch number", "Given by"}) {
            PdfPCell c = new PdfPCell(new Phrase(h, TH));
            c.setBackgroundColor(BRAND);
            c.setPadding(6);
            t.addCell(c);
        }
        boolean shaded = false;
        for (DoseLine d : data.doses()) {
            Color bg = shaded ? new Color(238, 244, 248) : Color.WHITE;
            for (String v : new String[] {d.vaccineName(), String.valueOf(d.doseNumber()),
                    d.dateAdministered().format(DATE), d.batchNumber() == null ? "-" : d.batchNumber(),
                    "Staff ID " + d.workerId()}) {
                PdfPCell c = new PdfPCell(new Phrase(v, VALUE));
                c.setBackgroundColor(bg);
                c.setPadding(5);
                t.addCell(c);
            }
            shaded = !shaded;
        }
        return t;
    }

    private PdfPTable verificationBlock(CertificateData data) throws WriterException, IOException, DocumentException {
        PdfPTable t = new PdfPTable(new float[] {1f, 3f});
        t.setWidthPercentage(100);
        t.setKeepTogether(true);

        Image qr = Image.getInstance(qrPng(data.verifyUrl()));
        qr.scaleToFit(90, 90);
        PdfPCell qrCell = new PdfPCell(qr, false);
        qrCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        t.addCell(qrCell);

        Paragraph text = new Paragraph();
        text.add(new Chunk("Verify this certificate\n", LABEL));
        text.add(new Chunk("Scan the QR code, or open the address below, to confirm this certificate was issued "
                + "by " + data.clinicName() + " and that the vaccination records still match.\n", VALUE));
        text.add(new Chunk(data.verifyUrl(), SMALL));
        PdfPCell textCell = new PdfPCell(text);
        textCell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        textCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(textCell);
        return t;
    }

    private static byte[] qrPng(String content) throws WriterException, IOException {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 240, 240);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", png);
        return png.toByteArray();
    }

    private static Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setLeading(height);
        return p;
    }

    /** Draws the clinic watermark under the content and a footer on every page. */
    private static final class PageDecorations extends PdfPageEventHelper {
        private final String clinicName;
        private final String certificateId;
        private final String verifyUrl;

        PageDecorations(String clinicName, String certificateId, String verifyUrl) {
            this.clinicName = clinicName;
            this.certificateId = certificateId;
            this.verifyUrl = verifyUrl;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            try {
                float w = document.getPageSize().getWidth();
                float h = document.getPageSize().getHeight();

                BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                PdfContentByte under = writer.getDirectContentUnder();
                PdfGState faint = new PdfGState();
                faint.setFillOpacity(0.10f);
                under.saveState();
                under.setGState(faint);
                under.beginText();
                under.setFontAndSize(bold, 56);
                under.setColorFill(BRAND);
                under.showTextAligned(Element.ALIGN_CENTER, clinicName.toUpperCase(Locale.ROOT), w / 2, h / 2, 45);
                under.endText();
                under.restoreState();

                PdfContentByte over = writer.getDirectContent();
                ColumnText.showTextAligned(over, Element.ALIGN_CENTER,
                        new Phrase("Certificate " + certificateId + "  |  Page " + writer.getPageNumber()
                                + "  |  Verify: " + verifyUrl, SMALL),
                        w / 2, 30, 0);
            } catch (DocumentException | IOException e) {
                throw new ExceptionConverter(e);
            }
        }
    }
}
