let razorpayScriptPromise = null;

/**
 * Dynamically loads the Razorpay checkout script (v1) if not already loaded.
 * Returns a Promise that resolves with the window.Razorpay constructor.
 */
export function loadRazorpay() {
  if (typeof window !== "undefined" && window.Razorpay) {
    return Promise.resolve(window.Razorpay);
  }

  if (razorpayScriptPromise) {
    return razorpayScriptPromise;
  }

  razorpayScriptPromise = new Promise((resolve, reject) => {
    if (typeof document === "undefined") {
      reject(new Error("Document is not available to load Razorpay script."));
      return;
    }

    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.async = true;
    script.onload = () => {
      if (window.Razorpay) {
        resolve(window.Razorpay);
      } else {
        reject(new Error("Razorpay SDK loaded but window.Razorpay is undefined."));
      }
    };
    script.onerror = () => {
      razorpayScriptPromise = null;
      reject(new Error("Failed to load Razorpay checkout script. Please check your network connection."));
    };

    document.body.appendChild(script);
  });

  return razorpayScriptPromise;
}

