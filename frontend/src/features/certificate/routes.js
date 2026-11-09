import { createElement } from "react";
import { CertificatePage } from "./CertificatePage";
import { VerifyCertificatePage } from "./VerifyCertificatePage";

export default [
  {
    path: "/certificates",
    element: createElement(CertificatePage),
    visibility: "protected",
  },
  {
    path: "/verify/certificate/:verificationCode",
    element: createElement(VerifyCertificatePage),
    visibility: "public",
  },
];
