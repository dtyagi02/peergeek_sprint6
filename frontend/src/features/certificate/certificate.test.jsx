import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { CertificatePage } from "./CertificatePage";
import { VerifyCertificatePage } from "./VerifyCertificatePage";
import { AdminCertificatesPanel } from "../admin/AdminCertificatesPanel";
import * as api from "./certificateApi";

vi.mock("./certificateApi", () => ({
  getMyCertificates: vi.fn(),
  reconcileCertificates: vi.fn(),
  retryCertificate: vi.fn(),
  getCertificateDownloadUrl: vi.fn(),
  verifyCertificate: vi.fn(),
  getAdminCertificates: vi.fn(),
  revokeAdminCertificate: vi.fn(),
  retryAdminCertificate: vi.fn(),
  reconcileAdminCertificates: vi.fn(),
}));

function renderWithClient(ui) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
    },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  );
}

describe("Certificate System Frontend Tests", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe("CertificatePage Component", () => {
    it("renders empty state when student has no certificates", async () => {
      api.reconcileCertificates.mockResolvedValue({ newlyIssued: 0 });
      api.getMyCertificates.mockResolvedValue([]);

      renderWithClient(<CertificatePage />);

      await waitFor(() => {
        expect(screen.getByText("My Certificates")).toBeInTheDocument();
      });
      expect(screen.getByText("No certificates earned yet")).toBeInTheDocument();
    });

    it("renders issued, failed, and revoked certificates with appropriate actions", async () => {
      const mockCertificates = [
        {
          id: 1,
          courseTitle: "React Architecture",
          instructorName: "Jane Doe",
          certificateNumber: "PG-REACT01",
          status: "ISSUED",
          issuedAt: "2026-11-01T10:00:00Z",
        },
        {
          id: 2,
          courseTitle: "Backend Microservices",
          instructorName: "John Smith",
          certificateNumber: "PG-JAVA02",
          status: "FAILED",
          issuedAt: null,
        },
      ];

      api.reconcileCertificates.mockResolvedValue({ newlyIssued: 1 });
      api.getMyCertificates.mockResolvedValue(mockCertificates);
      api.getCertificateDownloadUrl.mockResolvedValue("https://s3.example.com/cert.pdf");
      api.retryCertificate.mockResolvedValue({});

      const openSpy = vi.spyOn(window, "open").mockImplementation(() => null);

      renderWithClient(<CertificatePage />);

      await waitFor(() => {
        expect(screen.getByText("React Architecture")).toBeInTheDocument();
        expect(screen.getByText("Backend Microservices")).toBeInTheDocument();
      });

      expect(screen.getByText("Issued")).toBeInTheDocument();
      expect(screen.getByText("Failed")).toBeInTheDocument();

      // Download button
      const downloadBtn = screen.getByText("Download PDF");
      fireEvent.click(downloadBtn);
      await waitFor(() => {
        expect(api.getCertificateDownloadUrl).toHaveBeenCalledWith(1);
        expect(openSpy).toHaveBeenCalledWith("https://s3.example.com/cert.pdf", "_blank", "noopener,noreferrer");
      });

      // Retry button
      const retryBtn = screen.getByText("Retry Generation");
      fireEvent.click(retryBtn);
      await waitFor(() => {
        expect(api.retryCertificate).toHaveBeenCalledWith(2);
      });
    });

    it("displays error message and allows retry when reconciliation fails", async () => {
      api.reconcileCertificates.mockRejectedValue(new Error("Network timeout"));
      api.getMyCertificates.mockResolvedValue([]);

      renderWithClient(<CertificatePage />);

      await waitFor(() => {
        expect(screen.getByText("Network timeout")).toBeInTheDocument();
      });
      expect(screen.getByRole("button", { name: /retry/i })).toBeInTheDocument();
    });
  });

  describe("VerifyCertificatePage Component", () => {
    it("displays valid certificate information for ISSUED certificate", async () => {
      api.verifyCertificate.mockResolvedValue({
        valid: true,
        status: "ISSUED",
        certificateNumber: "PG-VERIFY01",
        studentName: "Devansh Tyagi",
        courseTitle: "Spring Boot Mastery",
        instructorName: "Dr. Jane",
        issuedAt: "2026-11-03T00:00:00Z",
      });

      render(
        <MemoryRouter initialEntries={["/verify/certificate/valid-token"]}>
          <Routes>
            <Route path="/verify/certificate/:verificationCode" element={<VerifyCertificatePage />} />
          </Routes>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText("Verified Certificate")).toBeInTheDocument();
      });
      expect(screen.getByText("Devansh Tyagi")).toBeInTheDocument();
      expect(screen.getByText("Spring Boot Mastery")).toBeInTheDocument();
      expect(screen.getByText("Instructor: Dr. Jane")).toBeInTheDocument();
      expect(screen.getByText("ID: PG-VERIFY01")).toBeInTheDocument();
    });

    it("displays revoked warning and reason for REVOKED certificate", async () => {
      api.verifyCertificate.mockResolvedValue({
        valid: false,
        status: "REVOKED",
        certificateNumber: "PG-REVOKED99",
        studentName: "Devansh Tyagi",
        courseTitle: "Security Engineering",
        instructorName: "Dr. Jane",
        revocationReason: "Academic policy violation",
        revokedAt: "2026-11-03T12:00:00Z",
      });

      render(
        <MemoryRouter initialEntries={["/verify/certificate/revoked-token"]}>
          <Routes>
            <Route path="/verify/certificate/:verificationCode" element={<VerifyCertificatePage />} />
          </Routes>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText("Certificate Revoked")).toBeInTheDocument();
      });
      expect(screen.getByText("Academic policy violation")).toBeInTheDocument();
      expect(screen.getByText("Certificate No Longer Valid")).toBeInTheDocument();
    });

    it("displays verification failed message for invalid or missing code", async () => {
      api.verifyCertificate.mockResolvedValue({ valid: false, status: null });

      render(
        <MemoryRouter initialEntries={["/verify/certificate/invalid-token"]}>
          <Routes>
            <Route path="/verify/certificate/:verificationCode" element={<VerifyCertificatePage />} />
          </Routes>
        </MemoryRouter>
      );

      await waitFor(() => {
        expect(screen.getByText("Verification Failed")).toBeInTheDocument();
      });
      expect(screen.getByText("Certificate Not Valid")).toBeInTheDocument();
    });
  });

  describe("AdminCertificatesPanel Component", () => {
    it("renders certificates table and allows admin revocation with reason", async () => {
      const mockAdminList = {
        content: [
          {
            id: 101,
            certificateNumber: "PG-ADM001",
            studentName: "Alice Smith",
            courseTitle: "Full-Stack Web Development",
            status: "ISSUED",
            issuedAt: "2026-11-01T10:00:00Z",
          },
        ],
        totalPages: 1,
      };

      api.getAdminCertificates.mockResolvedValue(mockAdminList);
      api.revokeAdminCertificate.mockResolvedValue({});
      api.reconcileAdminCertificates.mockResolvedValue({
        totalExamined: 15,
        newlyIssued: 4,
        alreadyIssued: 10,
        ineligible: 1,
      });

      renderWithClient(<AdminCertificatesPanel />);

      await waitFor(() => {
        expect(screen.getByText("Certificate Management")).toBeInTheDocument();
        expect(screen.getByText("PG-ADM001")).toBeInTheDocument();
        expect(screen.getByText("Alice Smith")).toBeInTheDocument();
      });

      // Click Revoke button
      const revokeBtn = screen.getByText("Revoke");
      fireEvent.click(revokeBtn);

      await waitFor(() => {
        expect(screen.getByText("Revoke Certificate")).toBeInTheDocument();
      });

      const reasonInput = screen.getByPlaceholderText(/Course requirements not legitimately completed/i);
      fireEvent.change(reasonInput, { target: { value: "Violated honor code" } });

      const confirmBtn = screen.getByText("Confirm Revocation");
      fireEvent.click(confirmBtn);

      await waitFor(() => {
        expect(api.revokeAdminCertificate).toHaveBeenCalledWith(101, "Violated honor code");
      });

      // Trigger Batch Reconciliation
      const reconcileBtn = screen.getByText("Run Batch Reconciliation");
      fireEvent.click(reconcileBtn);

      await waitFor(() => {
        expect(api.reconcileAdminCertificates).toHaveBeenCalled();
        expect(screen.getByText(/Examined 15 active enrollments/i)).toBeInTheDocument();
      });
    });
  });
});

