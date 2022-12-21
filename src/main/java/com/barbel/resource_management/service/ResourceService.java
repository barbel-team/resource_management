package com.barbel.resource_management.service;

import com.barbel.resource_management.dto.ResourceDto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ResourceService {
    static Long totalUseMemory = 0l;
    public String MonitorAll() throws IOException, InterruptedException {
        JSONArray result = new JSONArray();
        StringBuilder sb = new StringBuilder();

        ObjectMapper mapper = new ObjectMapper();
        String cmd = "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers/json";
        Process process = Runtime.getRuntime().exec(cmd);
        BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        JSONObject json = new JSONObject();

        if ((line = in.readLine()) != null) {
            List<Map<String, Object>> datas = mapper.readValue(line, new TypeReference<>() {
            });
            sb.append("container num : " + datas.size()+"\n");
            System.out.println("container num : " + datas.size()); // List size가 곧 컨테이너의 개수

            int availableCore = (Runtime.getRuntime().availableProcessors() / 2) * 100;
            sb.append("host cpu 정보 : " + availableCore + "%\n");
            System.out.println("host cpu 정보 : " + availableCore + "%");
            print(datas,json,sb);
        }

        System.out.println("---------------------------Monitor Sleep-------------------------------------------------------------------");
        Thread.sleep(1000); // 모니터링 주기 2초
        in.close();

        return sb.toString();
    }

    public List<ResourceDto> check() throws IOException
    {
        List<ResourceDto> list = new ArrayList<>();
        int i =0;
        String[] normalName={"member","product","memberDB","productDB"};
        String[] names = {"/member","/product","/memberDB","/productDB"};
        for(String name : names ) {
            ResourceDto rd = new ResourceDto();
            rd.setName(name);

            Process process = Runtime.getRuntime().exec("curl -s --unix-socket /var/run/docker.sock http://v1.41/containers" + name + "/stats");
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = in.readLine();

            Map<String, Object> stats = new ObjectMapper().readValue(line, new TypeReference<>() {
            });
            Map<String, Object> cpu_stats = (Map<String, Object>) stats.get("cpu_stats");

            Long cpu_num = Long.parseLong(cpu_stats.get("online_cpus").toString());
            Long CsystemCpuUsage = Long.parseLong(cpu_stats.get("system_cpu_usage").toString());

            Map<String, Object> cpu_stats2 = (Map<String, Object>) cpu_stats.get("cpu_usage");
            Long CtotalCpuUsage = Long.parseLong(cpu_stats2.get("total_usage").toString());
            Map<String, Object> pre_cpu_stats = (Map<String, Object>) stats.get("precpu_stats");
            Map<String, Object> pre_cpu_stats2 = (Map<String, Object>) pre_cpu_stats.get("cpu_usage");
            Long PtotalCpuUsage = Long.parseLong(pre_cpu_stats2.get("total_usage").toString());

            Double cpu_percent = (CtotalCpuUsage - PtotalCpuUsage) * cpu_num * 100.0 / CsystemCpuUsage;
            JSONObject jsonObject = new JSONObject();
            try{
                jsonObject = new JSONObject(
                        "{\n" +
                                "  \"BlkioWeight\": 300,\n" +
                                "  \"CpuShares\": 512,\n" +
                                "  \"CpuPeriod\": 100000,\n" +
                                "  \"CpuQuota\": 50000,\n" +
                                "  \"CpuRealtimePeriod\": 1000000,\n" +
                                "  \"CpuRealtimeRuntime\": 10000,\n" +
                                "  \"CpusetCpus\": \"0,1\",\n" +
                                "  \"CpusetMems\": \"0\",\n" +
                                "  \"Memory\": 314572800,\n" +
                                "  \"MemorySwap\": 514288000,\n" +
                                "  \"MemoryReservation\": 209715200,\n" +
                                "  \"KernelMemory\": 52428800,\n" +
                                "  \"RestartPolicy\": {\n" +
                                "    \"MaximumRetryCount\": 4,\n" +
                                "    \"Name\": \"on-failure\"\n" +
                                "  }\n" +
                                "}"
                );
            }catch (Exception e)
            {
                System.out.println("not parse");
                e.printStackTrace();
            }
            System.out.println(jsonObject.toString());
            if(cpu_percent > 80.0)
            {
                System.out.println("CPU P over 80%");
                Runtime.getRuntime().exec("curl --unix-socket /var/run/docker.sock -X POST -H \"Content-Type: application/json\" -d "+"\'"+jsonObject.toString()+"\'" + "http://v1.41/containers" + name + "/update")
                        ;
            }
            // "curl -s --unix-socket /var/run/docker.sock http://v1.41/containers" + name + "/update"
            else if(cpu_percent < 20.0)
            {

                System.out.println("CPU P under 20%");
                Process result = Runtime.getRuntime().exec("curl --unix-socket /var/run/docker.sock -X POST -H \"Content-Type: application/json\" -d "+ jsonObject.toString()+" http://v1.41/containers/member/update");
                BufferedReader in2 = new BufferedReader(new InputStreamReader(result.getInputStream()));
                String line2 = in2.readLine();
                System.out.println(line2);
            }

            Map<String, Object> memory_stats = (Map<String, Object>) stats.get("memory_stats");
            Long usage = Long.parseLong(memory_stats.get("usage").toString());
            Long used_memory = usage;
            Long memory_limit = Long.parseLong(memory_stats.get("limit").toString());
            Double memory_percent = (double) (used_memory) / memory_limit * 100.0;

            if(memory_percent > 80.0)
            {
                System.out.println("MEMORY P over 80%");
                Runtime.getRuntime().exec("docker update --memory \"512mb\" --memory-swap \"512mb\" "+normalName[i]);
            }
            else if(memory_percent < 20.0)
            {
                System.out.println("MEMORY P under 20%");
                Runtime.getRuntime().exec("docker update --memory \"2048mb\" --memory-swap \"2048mb\" "+normalName[i]);
            }
            System.out.println("check finish : "+name);
            list.add(rd);
            i++;
        }

        return list;
    }
    public static void print(List<Map<String, Object>> datas, JSONObject json,StringBuilder sb) throws IOException, InterruptedException {
        int length = datas.size(); // container 개수
        double CpuUse = 0f; // 모든 컨테이너의 cpu 사용량을 더하여 총 cpu 사용량을 구한다
        int i = 0;
        totalUseMemory = 0l;

        for (Map<String, Object> data : datas) // 컨테이너 개수만큼 반복문 실행
        {
            Map<String, Object> first = (Map<String, Object>) data.get("NetworkSettings");
            Map<String, Object> second = (Map<String, Object>) first.get("Networks");
            sb.append("--Container-->\n");
            System.out.println("----------------Container------------------");

            sb.append("Container Id : " + data.get("Id")+"\n");
            System.out.println("Container Id : " + data.get("Id")); // 컨테이너의 ID 확인

            String obj = data.get("Names").toString();
            obj = obj.substring(1, obj.length() - 1); // 각 컨테이너의 이름으로 docker engine에서 stats를 호출
            System.out.println("container name : " + obj.substring(1, obj.length())); // 컨테이너의 이름 호출
            sb.append("container name : " + obj.substring(1, obj.length())+"\n");
            // 수행중인 컨테이너의 stats 정보 호출
            Process process = Runtime.getRuntime().exec("curl -s --unix-socket /var/run/docker.sock http://v1.41/containers" + obj + "/stats");
            BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = in.readLine();
            Map<String, Object> stats = new ObjectMapper().readValue(line, new TypeReference<>() {
            });
            Map<String, Object> cpu_stats = (Map<String, Object>) stats.get("cpu_stats");

            Long cpu_num = Long.parseLong(cpu_stats.get("online_cpus").toString()); // 컨테이너마다 할당된 cpu 개수 확인
            System.out.println("Cpu num : " + cpu_stats.get("online_cpus"));
            sb.append("Cpu num : " + cpu_stats.get("online_cpus")+"\n");
            Map<String, Object> memory_stats = (Map<String, Object>) stats.get("memory_stats");

            Long CsystemCpuUsage = Long.parseLong(cpu_stats.get("system_cpu_usage").toString()); // system 전체의 cpu 사용량 확인
            Map<String, Object> cpu_stats2 = (Map<String, Object>) cpu_stats.get("cpu_usage");
            Long CtotalCpuUsage = Long.parseLong(cpu_stats2.get("total_usage").toString()); // 컨테이너의 총 cpu 사용량 확인
            Map<String, Object> pre_cpu_stats = (Map<String, Object>) stats.get("precpu_stats");
            Map<String, Object> pre_cpu_stats2 = (Map<String, Object>) pre_cpu_stats.get("cpu_usage");
            Long PtotalCpuUsage = Long.parseLong(pre_cpu_stats2.get("total_usage").toString());

            Double cpu_percent = (CtotalCpuUsage - PtotalCpuUsage) * cpu_num * 100.0 / CsystemCpuUsage;
            String percent = String.format("%.5f", cpu_percent);

            System.out.println("CPU 사용률 : " + percent + "%");
            sb.append("CPU 사용률 : " + percent + "%"+"\n");
            Long usage = Long.parseLong(memory_stats.get("usage").toString());
            Map<String, Object> memory_stats2 = (Map<String, Object>) memory_stats.get("stats");

            Long used_memory = usage;
            totalUseMemory += (used_memory / 8000000);

            Long memory_limit = Long.parseLong(memory_stats.get("limit").toString());

            Double memory_percent = (double) (used_memory) / memory_limit * 100.0;
            String m_percent = String.format("%.3f", memory_percent);
            System.out.println("Memory 점유률 : " + m_percent + "%");
            sb.append("Memory 점유률 : " + m_percent + "%\n");
            i++;
        }
    }


}
