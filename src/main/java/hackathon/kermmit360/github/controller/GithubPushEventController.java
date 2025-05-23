package hackathon.kermmit360.github.controller;

import hackathon.kermmit360.github.dto.GithubPushEventDto;
import hackathon.kermmit360.github.service.GithubEventService;
import hackathon.kermmit360.login.GithubLoginService;
import hackathon.kermmit360.member.dto.MemberDto;
import hackathon.kermmit360.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class GithubPushEventController {

    private final GithubEventService githubEventService;
    private final MemberService memberService;
    private final GithubLoginService githubLoginService;

    @PostMapping(value = "/home/api/integrate", params = "action=integrate")
    public String integrateWithGithub(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String username = resolveUsername(authentication);
        MemberDto.Response member = memberService.getMemberByUsername(username);
        GithubPushEventDto pushEventDto = githubEventService.fetchAndApplyAllExp();

        model.addAttribute("member", member);
        log.info("📦 GitHub Push Event DTO: {}", pushEventDto);
        model.addAttribute("languages", pushEventDto.getLanguages());

        applyCommitStatsToModel(pushEventDto, model);
        prepareChartData(pushEventDto, model);
        prepareLanguageChartData(pushEventDto, model);

        return "home";
    }

    @PostMapping(value = "/home/api/fake-commit", params = "action=fake-commit")
    public String fakeCommitEvent(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = resolveUsername(authentication);

        MemberDto.Response member = githubEventService.fakeCommit(username);
        model.addAttribute("member", member);
        model.addAttribute("recentRepo", "fake repo");
        model.addAttribute("dailyCommits", member.getExp());
        model.addAttribute("weeklyCommits", 0);
        model.addAttribute("monthlyCommits", 0);
        return "home";
    }

    public String resolveUsername(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            return githubLoginService.userLogin(oauthToken);
        }
        return authentication.getName();
    }

    public void applyCommitStatsToModel(GithubPushEventDto dto, Model model) {
        if (dto == null || dto.getCommitTimestamps() == null || dto.getCommitTimestamps().isEmpty()) {
            model.addAttribute("recentRepo", "없음");
            model.addAttribute("dailyCommits", 0);
            model.addAttribute("weeklyCommits", 0);
            model.addAttribute("monthlyCommits", 0);
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        LocalDate today = now.toLocalDate();
        LocalDate yesterday = today.minusDays(1);
        List<ZonedDateTime> timestamps = dto.getCommitTimestamps();

        long daily = timestamps.stream()
                .map(ZonedDateTime::toLocalDate)
                .filter(date -> date.equals(today) || date.equals(yesterday))
                .count();

        long weekly = timestamps.stream()
                .map(ZonedDateTime::toLocalDate)
                .filter(date -> !date.isBefore(today.minusDays(7)))
                .count();

        long monthly = timestamps.stream()
                .map(ZonedDateTime::toLocalDate)
                .filter(date -> !date.isBefore(today.minusMonths(1)))
                .count();

        model.addAttribute("recentRepo", dto.getRepoName());
        model.addAttribute("dailyCommits", daily);
        model.addAttribute("weeklyCommits", weekly);
        model.addAttribute("monthlyCommits", monthly);
    }

    public void prepareChartData(GithubPushEventDto dto, Model model) {
        Map<LocalDate, Integer> commitStats = dto.getCommitStats();
        List<String> dates = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        commitStats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    dates.add(entry.getKey().toString());
                    counts.add(entry.getValue());
                });

        model.addAttribute("commitDates", dates);
        model.addAttribute("commitCounts", counts);
    }

    public void prepareLanguageChartData(GithubPushEventDto dto, Model model) {
        Map<String, Integer> languageStats = dto.getLanguages();  // 푸시 이벤트에서 언어별 커밋 비율
        List<String> languages = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        languageStats.entrySet().stream()
                .forEach(entry -> {
                    languages.add(entry.getKey());
                    counts.add(entry.getValue());
                });

        model.addAttribute("languages", languages);
        model.addAttribute("languageCounts", counts);

        log.info("============ 언어 {}",languages);
    }
}