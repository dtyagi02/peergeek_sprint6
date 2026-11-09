import { createElement } from "react";
import { DirectChatPage, DirectMessagesPage } from "./DirectMessagesPage";

export default [
  {
    path: "/messages",
    element: createElement(DirectMessagesPage),
    visibility: "protected",
  },
  {
    path: "/messages/:conversationId",
    element: createElement(DirectChatPage),
    visibility: "protected",
  },
];
