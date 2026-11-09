export class ApiError extends Error {
  constructor({ status, code, message, fieldErrors = [] }) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}
