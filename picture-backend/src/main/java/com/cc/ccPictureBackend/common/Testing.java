package com.cc.ccPictureBackend.common;

import java.util.HashSet;

/**
 * @Author cuicui
 * @Description 名字检索
 */
@SuppressWarnings("all")
public class Testing {
    public static void main(String[] args) {
        String mother = "郑若欣、凌泽辉、杨文斌、李洋、郭银赛、王涛、童英、张瀚、王孟珂、张朕煜、廖章泽、曾亮、陈璐璐、王帅、谢宜杰、黄建强、刘瑞申、肖文文、李震宇、戚晗、吴昌昊、李金鹏、朱威然、蔡铠岳、朱金辉、刘捷、张伟涛、侯春宁、苏慧哲、周芄、翁星宇、朱伟健、刘以琳、苏慧哲、周芄、高剑奇、皇苏轼、Liu Jie、郑若欣、翁星宇、朱伟健、李鹏博、郭佳宇、李金鹏、黄建强、朱金辉、杨琪琪、梁静、刘超富、张逸骋、李鹏博、刘正扬、顾俊铨、李金鹏、朱金辉、翁星宇、周芄、杨琪琪、梁静、刘超富、张逸骋、李鹏博、刘正扬、顾俊铨、周芄、樊明森、魏苏波、陈汉峰、杨琪琪、梁静、郑若欣、刘正扬、黄典、高剑奇、吴杰、孙权、朱昌明、窦万春、孙权、曹健、丁志军、李鹏博、陈汉峰、顾峻铨、刘超富、张逸骋、郑若欣、王祖佳、龚泽昊、韩孟宇、刘正扬、黄典、周子剑、张伟涛、王涛、程莹、叶希臣、于泽强、张锦豪、程莹、叶希臣、于泽强、张锦豪";
        String[] motherSplit = mother.split("、");

        HashSet<String> mmhashSet = new HashSet<>();
        for (String s : motherSplit) {
            mmhashSet.add(s);
        }
        System.out.println(mmhashSet.size()); // 63
        System.out.println(mmhashSet.toString());

        // 去重处理


        String ss = "骆祥峰 魏晓 陈雪 沈俊 王昊 王艺璇 余航 孙妍 高剑奇 侯春宁 苏慧哲 李金鹏 王银 夏楠 李鹏博 吴昊 陶曦 司书康 于笑笑 陈志贵 王子健 赵征明 金伟强 霍宏斌 杜思远 顾河建 林逸 徐昌华 江婷婷 王乾慧 王涛 陈璐瑶 李韶杰 王斑 娄颖 陈显明 刘卫东 姚辉 付建刚 梁斌 周卫民";
        String[] split = ss.split(" ");

        // 将ss 加入到 hashSet，然后比较
        HashSet<String> sshashSet = new HashSet<>();
        for (String s : split) {
            sshashSet.add(s);
        }

        StringBuilder stringBuilder = new StringBuilder();
        for (String str:mmhashSet){
            if (!sshashSet.contains(str)){
                stringBuilder.append(str).append("、");
            }
        }

        System.out.println(stringBuilder.toString());
    }
}
