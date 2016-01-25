package sample.controller;

import com.lacunasoftware.restpki.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import sample.Application;
import sample.util.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@Controller
public class PadesSignatureController {

	/*
	 * This action initiates a PAdES signature using REST PKI and renders the signature page.
	 *
	 * Both PAdES signature examples, with a server file and with a file uploaded by the user, converge to this action.
	 * The difference is that, when the file is uploaded by the user, the action is called with a URL argument named
	 * "userfile".
	 */
	@RequestMapping(value = "/pades-signature", method = {RequestMethod.GET})
	public String get(
		@RequestParam(value = "userfile", required = false) String userfile,
		Model model,
		HttpServletResponse response
	) throws IOException, RestException {

		// Instantiate the PadesSignatureStarter class, responsible for receiving the signature elements and start the
		// signature process. For more information, see:
		// https://pki.rest/Content/docs/java-client/index.html?com/lacunasoftware/restpki/PadesSignatureStarter.html
		PadesSignatureStarter signatureStarter = new PadesSignatureStarter(Util.getRestPkiClient());

		if (userfile != null && !userfile.isEmpty()) {

			// If the URL argument "userfile" is filled, it means the user was redirected here by UploadController
			// (signature with file uploaded by user). We'll set the path of the file to be signed, which was saved in the
			// temporary folder by UploadController (such a file would normally come from your application's database)
			signatureStarter.setPdfToSign(Application.getTempFolderPath().resolve(userfile));

		} else {

			// If both userfile is null, this is the "signature with server file" case. We'll set the file to
			// be signed as a byte array
			signatureStarter.setPdfToSign(Util.getSampleDocContent());

		}

		// Set the signature policy
		signatureStarter.setSignaturePolicy(SignaturePolicy.PadesBasic);

		// Set a SecurityContext to be used to determine trust in the certificate chain
		signatureStarter.setSecurityContext(SecurityContext.pkiBrazil);
		// Note: By changing the SecurityContext above you can accept only certificates from a certain PKI,
		// for instance, ICP-Brasil (SecurityContext.pkiBrazil).

		// Create a visual representation for the signature
		PadesVisualRepresentation visualRepresentation = new PadesVisualRepresentation();

		// Set the text that will be inserted in the signature visual representation with the date time of the signature.
		// The tags {{signerName}} and {{signerNationalId}} will be substituted according to the user's certificate
		// signerName       -> full name of the signer
		// signerNationalId -> if the certificate is ICP-Brasil, contains the signer's CPF
		PadesVisualText text = new PadesVisualText("Assinado por {{signerName}} ({{signerNationalId}})", true);
		// Optionally set the text horizontal alignment (Left or Right), if not set the default is Left.
		text.setHorizontalAlign(PadesTextHorizontalAlign.Left);
		visualRepresentation.setText(text);

		// Set a image to stamp the signature visual representation
		visualRepresentation.setImage(new PadesVisualImage(Util.getPdfStampContent(), "image/png"));

		// Position of the visual representation. We have encapsulated this code in a method to include several
		// possibilities depending on the argument passed to the function. Experiment changing the argument to see
		// different examples of signature positioning (valid values are 1-6). Once you decide which is best for your
		// case, you can place the code directly here.
		visualRepresentation.setPosition(getVisualRepresentationPosition(4));

		// Set the visual representation created
		signatureStarter.setVisualRepresentation(visualRepresentation);

		// Call the startWithWebPki() method, which initiates the signature. This yields the token, a 43-character
		// case-sensitive URL-safe string, which identifies this signature process. We'll use this value to call the
		// signWithRestPki() method on the Web PKI component (see file signature-form.js) and also to complete the
		// signature after the form is submitted (see method post() below). This should not be mistaken with the API
		// access token.
		String token = signatureStarter.startWithWebPki();

		// The token acquired above can only be used for a single signature attempt. In order to retry the signature it is
		// necessary to get a new token. This can be a problem if the user uses the back button of the browser, since the
		// browser might show a cached page that we rendered previously, with a now stale token. To prevent this from
		// happening, we call the method Util.setNoCacheHeaders(), which sets HTTP headers to prevent caching of the page.
		Util.setNoCacheHeaders(response);

		// Render the signature page (templates/pades-signature.html)
		model.addAttribute("token", token);
		model.addAttribute("userfile", userfile);
		return "pades-signature";
	}

	// This method is called by the get() method. It contains examples of signature visual representation positionings.
	private PadesVisualPositioning getVisualRepresentationPosition(int sampleNumber) throws RestException {

		switch (sampleNumber) {

			case 1:
				// Example #1: automatic positioning on footnote. This will insert the signature, and future signatures,
				// ordered as a footnote of the last page of the document
				return PadesVisualPositioning.getFootnote(Util.getRestPkiClient());

			case 2:
				// Example #2: get the footnote positioning preset and customize it
				PadesVisualAutoPositioning footnotePosition = PadesVisualPositioning.getFootnote(Util.getRestPkiClient());
				footnotePosition.getContainer().setLeft(2.54);
				footnotePosition.getContainer().setBottom(2.54);
				footnotePosition.getContainer().setRight(2.54);
				return footnotePosition;

			case 3:
				// Example #3: automatic positioning on new page. This will insert the signature, and future signatures,
				// in a new page appended to the end of the document.
				return PadesVisualPositioning.getNewPage(Util.getRestPkiClient());

			case 4:
				// Example #4: get the "new page" positioning preset and customize it
				PadesVisualAutoPositioning newPagePos = PadesVisualPositioning.getNewPage(Util.getRestPkiClient());
				newPagePos.getContainer().setLeft(2.54);
				newPagePos.getContainer().setTop(2.54);
				newPagePos.getContainer().setRight(2.54);
				newPagePos.setSignatureRectangleSize(new PadesSize(5, 3));
				return newPagePos;

			case 5:
				// Example #5: manual positioning
				PadesVisualRectangle pos = new PadesVisualRectangle();
				// define a manual position of 5cm x 3cm, positioned at 1 inch from the left and bottom margins
				pos.setWidthLeftAnchored(5.0, 2.54);
				pos.setHeightBottomAnchored(3.0, 2.54);
				return new PadesVisualManualPositioning(
					0, // Page number. Zero means the signature will be placed on a new page appended to the end of the document
					PadesMeasurementUnits.Centimeters,
					pos // reference to the manual position defined above
				);

			case 6:
				// Example #6: custom auto positioning
				PadesVisualRectangle container = new PadesVisualRectangle();
				// Specification of the container where the signatures will be placed, one after the other
				container.setHorizontalStretch(2.54, 2.54); // variable-width container with the given margins
				container.setHeightBottomAnchored(12.31, 2.54); // bottom-aligned fixed-height container
				return new PadesVisualAutoPositioning(
					-1, // Page number. Negative values represent pages counted from the end of the document (-1 is last page)
					PadesMeasurementUnits.Centimeters,
					container, // Reference to the container defined above
					new PadesSize(5.0, 3.0), // Specification of the size of each signature rectangle
					1.0 // The signatures will be placed in the container side by side. If there's no room left, the signatures
					    // will "wrap" to the next row. This value specifies the vertical distance between rows
				);

			default:
				return null;
		}
	}

	/*
	 * This action receives the form submission from the signature page. We'll call REST PKI to complete the signature.
	 */
	@RequestMapping(value = "/pades-signature", method = {RequestMethod.POST})
	public String post(
		@RequestParam(value = "token", required = true) String token,
		Model model
	) throws IOException, RestException {

		// Instantiate the PadesSignatureFinisher class, responsible for completing the signature process. For more
		// information, see:
		// https://pki.rest/Content/docs/java-client/index.html?com/lacunasoftware/restpki/PadesSignatureFinisher.html
		PadesSignatureFinisher signatureFinisher = new PadesSignatureFinisher(Util.getRestPkiClient());

		// Set the token for this signature (rendered in a hidden input field, see file templates/pades-signature.html)
		signatureFinisher.setToken(token);

		// Call the finish() method, which finalizes the signature process and returns the signed PDF's bytes
		byte[] signedPdf = signatureFinisher.finish();

		// Get information about the certificate used by the user to sign the file. This method must only be called after
		// calling the finish() method.
		PKCertificate signerCert = signatureFinisher.getCertificateInfo();

		// At this point, you'd typically store the signed PDF on your database. For demonstration purposes, we'll
		// store the PDF on a temporary folder and return to the page an identifier that can be used to download it.

		String filename = UUID.randomUUID() + ".pdf";
		Files.write(Application.getTempFolderPath().resolve(filename), signedPdf);
		model.addAttribute("signerCert", signerCert);
		model.addAttribute("filename", filename);
		return "pades-signature-info";
	}
}
