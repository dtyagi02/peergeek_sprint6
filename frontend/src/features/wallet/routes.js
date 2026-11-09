import { createElement } from "react";
import { MyWalletPage } from "./MyWalletPage";

export default [
  {
    path: "/wallet",
    element: createElement(MyWalletPage),
    visibility: "protected",
  },
];

