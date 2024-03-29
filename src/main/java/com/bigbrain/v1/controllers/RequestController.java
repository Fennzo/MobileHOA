package com.bigbrain.v1.controllers;

import com.bigbrain.v1.DAOandRepositories.MaintenanceRepository;
import com.bigbrain.v1.models.Addresses;
import com.bigbrain.v1.models.Maintenances;
import com.bigbrain.v1.models.Requests;
import com.bigbrain.v1.models.Users;
import com.bigbrain.v1.DAOandRepositories.AddressRepository;
import com.bigbrain.v1.DAOandRepositories.RequestRepository;
import com.bigbrain.v1.services.ParseErrorMessageService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class RequestController {

    private RequestRepository requestRepository;
    private AddressRepository addressRepository;
    private MaintenanceRepository maintenanceRepository;
    private ParseErrorMessageService parseErrorMessageService;

    @Autowired
    public RequestController(RequestRepository requestRepository, AddressRepository addressRepository, MaintenanceRepository maintenanceRepository, ParseErrorMessageService parseErrorMessageService) {
        this.requestRepository = requestRepository;
        this.parseErrorMessageService = parseErrorMessageService;
        this.addressRepository = addressRepository;
        this.maintenanceRepository = maintenanceRepository;
    }

    @GetMapping("/user/requestform")
    public String showRequestForm(Model model, HttpSession httpSession) {
        Users user = (Users) httpSession.getAttribute("user");
        Requests request = new Requests();
        request.setRequestUserIDFK(user.getUserIdPK());
        model.addAttribute("newRequest", request);
        return "submitrequestform" ;
    }

    @PostMapping("/user/requestform")
    public String submitRequestForm(@ModelAttribute("newRequest") Requests newRequest, Model model) {

        Addresses address = addressRepository.findByUserID(newRequest.getRequestUserIDFK());
        newRequest.setAddressIDFK(address.getAddressIDPK());
        // assign maintenance
        int maintenanceAssignment = assignMaintenance();
        if (maintenanceAssignment != -1) {
            newRequest.setMaintenanceIdFK(maintenanceAssignment);
            newRequest.setStatus(Requests.Statuses.Assigned.toString());
        } else newRequest.setStatus(Requests.Statuses.Received.toString());

        // System.out.println("NEW REQUESTS: " + newRequest.toString());
        try {
            requestRepository.save(newRequest);
            return "redirect:/user/userrequests";
        } catch (Exception e) {
            String parsedMessage = parseErrorMessageService.parseErrorMessage(e.getMessage());
            model.addAttribute("errorMessage", parsedMessage);
            return "submitrequestform" ;
        }

    }

    @GetMapping("/user/userrequests")
    public String showUserRequests(HttpSession httpSession, Model model) {
        Users user = (Users) httpSession.getAttribute("user");
        List<Requests> userRequests = requestRepository.findAllByUserIdFk(user.getUserIdPK());
        model.addAttribute("userRequests", userRequests);
        return "userrequests" ;
    }

    @GetMapping("/admin/alluserrequests")
    public String showAllUserRequests(Model model) {
        List<Requests> allUserRequests = requestRepository.findAll();
        // System.out.println("allrequests: " + allUserRequests.toString());
        model.addAttribute("allUserRequests", allUserRequests);
        // model.addAttribute("user", user);
        return "allrequests" ;
    }

    @GetMapping("/deleterequest/{requestIDPK}")
    public String deleteRequest(@PathVariable int requestIDPK, Model model, HttpSession httpSession) {
        Users user = (Users) httpSession.getAttribute("user");
        model.addAttribute("user", user);
        requestRepository.deleteById(requestIDPK);

        if (user.getRole().equals("Manager")){
            return "redirect:/admin/alluserrequests";
        }
        else{
            return "redirect:/user/userrequests";
        }
    }

    @GetMapping("/updaterequest/{requestIDPK}")
    public String updateRequest(@PathVariable int requestIDPK, Model model, HttpSession httpSession) {
        Requests requestToUpdate = requestRepository.findById(requestIDPK);
        Users user = (Users) httpSession.getAttribute("user");
        model.addAttribute("user", user);
        model.addAttribute("requestToUpdate", requestToUpdate);
        return "requestupdateform" ;
    }

    @PostMapping("/updaterequest")
    public String submitUpdateRequest(@ModelAttribute("requestToUpdate") Requests requestToUpdate, HttpSession httpSession, Model model) {
        Users user = (Users) httpSession.getAttribute("user");
        requestRepository.update(requestToUpdate, requestToUpdate.getRequestIDPK());
        model.addAttribute("user", user);
        if (user.getRole().equals("Manager")){
            return "redirect:/admin/alluserrequests";
        }
        else{
            return "redirect:/user/userrequests";
        }
    }

    @GetMapping("/maintenance/assignedrequests")
    public String viewAllAssignedRequests(HttpSession httpSession, Model model) {
        Users user = (Users) httpSession.getAttribute("user");
        int maintenanceIdPk = maintenanceRepository.getMaintenanceIdPk(user.getUserIdPK());
        List<Requests> assignedRequests = requestRepository.findAllByMaintenanceIdFk(maintenanceIdPk);
        model.addAttribute("assignedRequests", assignedRequests);
        System.out.println("assigned requests:" + assignedRequests);
        return "Maintenanceallrequests" ;
    }

    @ResponseBody
    @PostMapping("/maintenance/assignedrequests/{requestIDPK}")// receive requestToUpdate
    public String updateRequestStatues(@PathVariable int requestIDPK, @RequestParam String requestStatus) {
        Requests requestToUpdate = requestRepository.findById(requestIDPK);
        requestToUpdate.setStatus(requestStatus);
       // System.out.println("Maintenance update request " + requestToUpdate + requestStatus);
        requestRepository.update(requestToUpdate, requestIDPK);
        return "ok";
    }

    public int assignMaintenance() {
        List<Maintenances> allMaintenances = maintenanceRepository.findAll();
        System.out.println(allMaintenances.toString());
        for (Maintenances maintenance : allMaintenances) {
            if (maintenance.getAvailability().equals("Available") && maintenance.getNumberOfRequests() <= 5)
                System.out.println("MaintenanceID: " + maintenance.getMaintenanceIdPk());
            return maintenance.getMaintenanceIdPk();
        }
        return -1;
    }
}
