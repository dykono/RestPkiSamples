﻿/*
 * This javascript file contains the client-side logic for the authentication example. Other parts can be found at:
 * - View: src/main/resources/templates/authentication.html
 * - Server-side logic: src/main/java/sample/controller/AuthenticationController
 *
 * This code uses the Lacuna Web PKI component to access the user's certificates. For more information, see
 * https://webpki.lacunasoftware.com/#/Documentation
 */

// -------------------------------------------------------------------------------------------------
// Global variables
// -------------------------------------------------------------------------------------------------

// >>> NOTE: This sample will only work on localhost (any port is fine). In order to run this sample online, you'll
// need a license for the Web PKI component (set it below if you have one).
var webPkiLicense = null;

var pki = new LacunaWebPKI(webPkiLicense);
var selectedCertThumbprint = null;
var nonce = null;
var certificate = null;

// -------------------------------------------------------------------------------------------------
// Function called once the page is loaded
// -------------------------------------------------------------------------------------------------
function init() {

	// Wireup of button clicks
	$('#signInButton').click(signIn);
	$('#refreshButton').click(refresh);

	// Block the UI while we get things ready
	$.blockUI();

	// Call the init() method on the LacunaWebPKI object, passing a callback for when
	// the component is ready to be used and another to be called when an error occurs
	// on any of the subsequent operations. For more information, see:
	// https://webpki.lacunasoftware.com/#/Documentation#coding-the-first-lines
	// http://webpki.lacunasoftware.com/Help/classes/LacunaWebPKI.html#method_init
	pki.init({
		ready: onWebPkiReady,
		defaultError: onWebPkiError // generic error callback on src/main/resources/static/js/app/site.js
	});
}

// -------------------------------------------------------------------------------------------------
// Function called once the Lacuna Web PKI component is ready to be used
// -------------------------------------------------------------------------------------------------
function onWebPkiReady() {
	// Invoke the loading of the certificates (note that the UI is already blocked)
	loadCertificates();
}

// -------------------------------------------------------------------------------------------------
// Function called when the user clicks the "Refresh" button
// -------------------------------------------------------------------------------------------------
function refresh() {
	// Block the UI while we load the certificates
	$.blockUI();
	// Invoke the loading of the certificates
	loadCertificates();
}

// -------------------------------------------------------------------------------------------------
// Function that loads the certificates, either on startup or when the user
// clicks the "Refresh" button. At this point, the UI is already blocked.
// -------------------------------------------------------------------------------------------------
function loadCertificates() {

	var select = $('#certificateSelect');

	// Clear the existing items on the dropdown
	select.find('option').remove();

	// Call listCertificates() on the LacunaWebPKI object. For more information see
	// http://webpki.lacunasoftware.com/Help/classes/LacunaWebPKI.html#method_listCertificates
	pki.listCertificates().success(function (certs) {

		// This anonymous function is called asynchronously once the listCertificates operation completes.
		// We'll populate the certificateSelect dropdown with the certificates, placing the
		// "thumbprint" property of each certificate on the value attribute of each item (this will be important later on).
		$.each(certs, function () {
			select.append(
				$('<option />')
				.val(this.thumbprint) // Don't change what is used as the value attribute
				.text(this.subjectName + ' (issued by ' + this.issuerName + ')') // You may customize here what is displayed for each item
			);
		});

		// Unblock the UI
		$.unblockUI();

	});
}

// -------------------------------------------------------------------------------------------------
// Function called when the user clicks the "Sign In" button
// -------------------------------------------------------------------------------------------------
function signIn() {

	// Block the UI while we process the authentication
	$.blockUI();

	// Get the value attribute of the option selected on the dropdown. Since we placed the "thumbprint"
	// property on the value attribute of each item (see function loadCertificates above), we're actually
	// retrieving the thumbprint of the selected certificate.
	selectedCertThumbprint = $('#certificateSelect').val();

	// Reset global state
   certificate = null;
   nonce = null;

	// Call the server to initiate the authentication (for more information see method
	// AuthenticationController.get(). The server will yield a nonce, which we'll store in a global variable. If the
	// variable is already filled, it means we already started the process, so we'll skip to the next step.
	if (nonce) {
		onAuthStarted();
	} else {
	    // Call the server to initiate the authentication (for more information see method AuthenticationController.Get())
	    $.ajax({
	        method: 'GET',
	        url: '/api/authentication',
	        success: function (response) {
	            nonce = response;
	            onAuthStarted();
	        },
	        error: onServerError // generic error callback on Content/js/app/site.js
	    });
	}
}

// -------------------------------------------------------------------------------------------------
// Function called when the server replies with a nonce
// -------------------------------------------------------------------------------------------------
function onAuthStarted() {
	// Call readCertificate() on the Web PKI component passing the selected certificate's thumbprint. This
	// reads the certificate's encoding, which we'll need later on. For more information, see
	// http://webpki.lacunasoftware.com/Help/classes/LacunaWebPKI.html#method_readCertificate
	pki.readCertificate(selectedCertThumbprint).success(onCertificateRetrieved); // callback for when the operation completes
}

// -------------------------------------------------------------------------------------------------
// Function called once the user's certificate encoding has been read
// -------------------------------------------------------------------------------------------------
function onCertificateRetrieved(cert) {

	// Store the certificate encoding on a global variable (we'll need it later)
	certificate = cert;

	// Call signData() on the Web PKI component to sign the nonce using the user's certificate
	pki.signData({
		thumbprint: selectedCertThumbprint, // selected certificate's thumbprint
		data: nonce,                        // cryptographic nonce generated by REST PKI
		digestAlgorithm: 'SHA-256'
	}).success(onSignatureCompleted); // callback for when the operation completes

	// Note on the encodings: the parameter 'data' in the call above is a byte array encoded as a Base64 string, which
	// is precisely what we are yielding in the method AuthenticationController.get().
}

// -------------------------------------------------------------------------------------------------
// Function called once the signature is completed
// -------------------------------------------------------------------------------------------------
function onSignatureCompleted(signature) {
	// Call the server to complete the authentication (for more information see method AuthenticationController.post())
	$.ajax({
		method: 'POST',
		url: '/api/authentication',
		data: JSON.stringify({
			certificate: certificate, // the encoding of the user's certificate (stored on a global variable)
			nonce: nonce,             // the nonce which was signed (stored on global variable)
			signature: signature      // the signature operation output
		}),
		contentType: 'application/json',
		success: onProcessCompleted, // success callback
		error: onServerError // generic error callback on src/main/resources/static/js/app/site.js
	});

	// Note on the encodings: the method AuthenticationController.post() expects the parameters certificate, nonce
	// and signature as byte arrays encoded as Base64 strings, which is precisely what the Web PKI component yields.

	// From this point on, we cannot reuse the nonce we stored on the global variable, so let's clear it.
	nonce = null;
}

// -------------------------------------------------------------------------------------------------
// Function called once the server replies with the result of the authentication
// -------------------------------------------------------------------------------------------------
function onProcessCompleted(data, textStatus, jqXHR) {

	if (data.success) {

		// If success, normally the user would register the user as signed in, and we'd redirect
		// him to somewhere else (his "dashboard", for instance) with the UI still blocked, with:
		//document.location.href = '/Dashboard';

		// However, in order to keep this sample as simple as possible, we haven't included in
		// the server-side the logic for registering a user as authenticated and storing that
		// state in the session. The server is simply replying with a success and something on
		// the message to show that we have access to the certificate's fields. So, for
		// demonstration purposes, we're just going to display that message.
		$.unblockUI();
		addAlert('success', data.message);
		$('#validationResultsPanel').hide();

	} else {

		// If the authentication failed, we'll alert the user and unblock the UI so that
		// he can try again with another certificate.

		// Unblock the UI
		$.unblockUI();

		// Render the failure message
		addAlert('danger', data.message); // the addAlert function is located on the file src/main/resources/static/js/app/site.js

		// If the response includes a validationResults, display it
		if (data.validationResults) {
			var panel = $('#validationResultsPanel');
			panel.find('textarea').val(data.validationResults);
			panel.show();
		}

	}
}

// Schedule the init function to be called once the page is loaded
$(document).ready(init);